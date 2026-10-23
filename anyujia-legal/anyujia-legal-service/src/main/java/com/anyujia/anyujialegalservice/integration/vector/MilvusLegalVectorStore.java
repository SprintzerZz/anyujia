package com.anyujia.anyujialegalservice.integration.vector;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.service.LegalVectorStore;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "legal.ai.milvus", name = "enabled", havingValue = "true")
public class MilvusLegalVectorStore implements LegalVectorStore {
    private static final String FIELD_CHUNK_ID = "chunk_id";
    private static final String FIELD_SOURCE_ID = "source_id";
    private static final String FIELD_ARTICLE = "article_number";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_URL = "source_url";
    private static final String FIELD_REGION = "region";
    private static final String FIELD_STATE = "state";
    private static final String FIELD_VECTOR = "embedding";

    private final LegalAiProperties properties;
    private final Gson gson = new Gson();
    private volatile MilvusClientV2 client;
    private volatile boolean available;

    public MilvusLegalVectorStore(LegalAiProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<VectorHit> search(double[] queryVector, int topK, Collection<Long> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty() || queryVector == null || queryVector.length == 0) {
            return List.of();
        }
        if (!isAvailable()) {
            return List.of();
        }
        try {
            LegalAiProperties.Milvus config = properties.getMilvus();
            String filter = "source_id in [" + sourceIds.stream()
                    .map(String::valueOf).collect(Collectors.joining(", ")) + "]";
            SearchResp response = client.search(SearchReq.builder()
                    .collectionName(config.getCollection())
                    .data(List.of(new FloatVec(toFloatArray(queryVector))))
                    .filter(filter)
                    .limit(Math.min(topK, config.getTopK()))
                    .outputFields(List.of(FIELD_CHUNK_ID))
                    .build());
            List<VectorHit> hits = new ArrayList<>();
            if (response.getSearchResults() != null) {
                for (List<SearchResp.SearchResult> group : response.getSearchResults()) {
                    for (SearchResp.SearchResult result : group) {
                        hits.add(new VectorHit(((Number) result.getId()).longValue(), result.getScore()));
                    }
                }
            }
            return hits;
        } catch (Exception ex) {
            available = false;
            log.warn("Milvus vector search failed; in-memory vector fallback remains active: {}",
                    ex.getMessage());
            return List.of();
        }
    }

    @Override
    public void upsert(long chunkId, long sourceId, String articleNumber, String title,
                       String sourceUrl, String region, double[] vector) {
        if (vector == null || vector.length == 0 || !isAvailable()) {
            return;
        }
        try {
            LegalAiProperties.Milvus config = properties.getMilvus();
            JsonObject row = new JsonObject();
            row.addProperty(FIELD_CHUNK_ID, chunkId);
            row.addProperty(FIELD_SOURCE_ID, sourceId);
            row.addProperty(FIELD_ARTICLE, nullable(articleNumber));
            row.addProperty(FIELD_TITLE, nullable(title));
            row.addProperty(FIELD_URL, nullable(sourceUrl));
            row.addProperty(FIELD_REGION, nullable(region));
            row.addProperty(FIELD_STATE, "ACTIVE");
            row.add(FIELD_VECTOR, gson.toJsonTree(toFloatArray(vector)));
            client.insert(InsertReq.builder()
                    .collectionName(config.getCollection())
                    .data(List.of(row))
                    .build());
        } catch (Exception ex) {
            available = false;
            log.warn("Milvus vector upsert failed for chunk {}: {}", chunkId, ex.getMessage());
        }
    }

    @Override
    public void deleteBySourceId(long sourceId) {
        if (!isAvailable()) {
            return;
        }
        try {
            LegalAiProperties.Milvus config = properties.getMilvus();
            client.delete(DeleteReq.builder()
                    .collectionName(config.getCollection())
                    .filter("source_id in [" + sourceId + "]")
                    .build());
        } catch (Exception ex) {
            available = false;
            log.warn("Milvus vector delete by source {} failed: {}", sourceId, ex.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        ensureClient();
        return available;
    }

    private MilvusClientV2 ensureClient() {
        MilvusClientV2 local = client;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            local = client;
            if (local == null) {
                local = buildClient();
                client = local;
                try {
                    ensureCollection(local);
                    available = true;
                } catch (Exception ex) {
                    available = false;
                    log.warn("Milvus vector store unavailable; in-memory vector fallback remains active: {}",
                            ex.getMessage());
                }
            }
            return client;
        }
    }

    private MilvusClientV2 buildClient() {
        LegalAiProperties.Milvus config = properties.getMilvus();
        var builder = ConnectConfig.builder()
                .uri("http://" + config.getHost() + ":" + config.getPort());
        if (config.getDatabase() != null && !config.getDatabase().isBlank()
                && !"default".equals(config.getDatabase())) {
            builder.dbName(config.getDatabase());
        }
        return new MilvusClientV2(builder.build());
    }

    private void ensureCollection(MilvusClientV2 client) {
        LegalAiProperties.Milvus config = properties.getMilvus();
        String collection = config.getCollection();
        boolean exists = client.hasCollection(HasCollectionReq.builder()
                .collectionName(collection).build());
        if (!exists) {
            createCollection(client, collection, config.getDimension());
        }
        client.loadCollection(LoadCollectionReq.builder().collectionName(collection).build());
    }

    private void createCollection(MilvusClientV2 client, String collection, int dimension) {
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_CHUNK_ID).dataType(DataType.Int64)
                .isPrimaryKey(Boolean.TRUE).autoID(Boolean.FALSE).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_SOURCE_ID).dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_ARTICLE).dataType(DataType.VarChar).maxLength(128).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_TITLE).dataType(DataType.VarChar).maxLength(512).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_URL).dataType(DataType.VarChar).maxLength(2048).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_REGION).dataType(DataType.VarChar).maxLength(32).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_STATE).dataType(DataType.VarChar).maxLength(16).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_VECTOR).dataType(DataType.FloatVector).dimension(dimension).build());
        client.createCollection(CreateCollectionReq.builder()
                .collectionName(collection).collectionSchema(schema).build());

        Map<String, Object> extraParams = new HashMap<>();
        extraParams.put("M", 16);
        extraParams.put("efConstruction", 200);
        IndexParam index = IndexParam.builder()
                .fieldName(FIELD_VECTOR)
                .indexName("idx_embedding_hnsw")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(extraParams)
                .build();
        client.createIndex(CreateIndexReq.builder()
                .collectionName(collection).indexParams(List.of(index)).build());
    }

    private float[] toFloatArray(double[] vector) {
        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = (float) vector[i];
        }
        return result;
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }
}
