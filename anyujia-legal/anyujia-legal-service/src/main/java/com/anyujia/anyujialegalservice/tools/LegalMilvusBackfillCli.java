package com.anyujia.anyujialegalservice.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.milvus.v2.service.vector.request.InsertReq;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Idempotent backfill of existing MySQL chunk embeddings into Milvus. */
public final class LegalMilvusBackfillCli {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Gson GSON = new Gson();

    private LegalMilvusBackfillCli() {
    }

    public static void main(String[] args) throws Exception {
        String host = LegalEmbeddingBackfillCli.env("LEGAL_MILVUS_HOST", "127.0.0.1");
        int port = Integer.parseInt(LegalEmbeddingBackfillCli.env("LEGAL_MILVUS_PORT", "19530"));
        String collection = LegalEmbeddingBackfillCli.env("LEGAL_MILVUS_COLLECTION", "legal_source_chunk_v1");
        int dimension = Integer.parseInt(LegalEmbeddingBackfillCli.env("LEGAL_MILVUS_DIMENSION", "1024"));

        String jdbcUrl = "jdbc:mysql://" + LegalEmbeddingBackfillCli.env("MYSQL_HOST", "127.0.0.1")
                + ":" + LegalEmbeddingBackfillCli.env("MYSQL_PORT", "3306") + "/"
                + LegalEmbeddingBackfillCli.env("MYSQL_DATABASE", "frameworkjava_dev")
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true";

        List<Row> rows = loadRows(jdbcUrl);
        if (rows.isEmpty()) {
            System.out.println("no embedded chunks found; nothing to backfill");
            return;
        }
        MilvusClientV2 client = connect(host, port);
        ensureCollection(client, collection, dimension);
        int batchSize = 100;
        int upserted = 0;
        for (int start = 0; start < rows.size(); start += batchSize) {
            List<Row> batch = rows.subList(start, Math.min(start + batchSize, rows.size()));
            client.insert(InsertReq.builder()
                    .collectionName(collection)
                    .data(batch.stream().map(LegalMilvusBackfillCli::toJson).toList())
                    .build());
            upserted += batch.size();
            System.out.printf("upserted=%d remaining=%d%n", upserted, rows.size() - upserted);
        }
        System.out.printf("backfill_complete=%d collection=%s%n", upserted, collection);
    }

    private static MilvusClientV2 connect(String host, int port) {
        ConnectConfig config = ConnectConfig.builder()
                .uri("http://" + host + ":" + port)
                .build();
        return new MilvusClientV2(config);
    }

    private static void ensureCollection(MilvusClientV2 client, String collection, int dimension) {
        if (client.hasCollection(HasCollectionReq.builder().collectionName(collection).build())) {
            client.loadCollection(LoadCollectionReq.builder().collectionName(collection).build());
            return;
        }
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
        schema.addField(AddFieldReq.builder()
                .fieldName("chunk_id").dataType(DataType.Int64)
                .isPrimaryKey(Boolean.TRUE).autoID(Boolean.FALSE).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("source_id").dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("article_number").dataType(DataType.VarChar).maxLength(128).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("title").dataType(DataType.VarChar).maxLength(512).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("source_url").dataType(DataType.VarChar).maxLength(2048).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("region").dataType(DataType.VarChar).maxLength(32).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("state").dataType(DataType.VarChar).maxLength(16).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("embedding").dataType(DataType.FloatVector).dimension(dimension).build());
        client.createCollection(CreateCollectionReq.builder()
                .collectionName(collection).collectionSchema(schema).build());

        Map<String, Object> extraParams = new HashMap<>();
        extraParams.put("M", 16);
        extraParams.put("efConstruction", 200);
        IndexParam index = IndexParam.builder()
                .fieldName("embedding")
                .indexName("idx_embedding_hnsw")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(extraParams)
                .build();
        client.createIndex(CreateIndexReq.builder()
                .collectionName(collection).indexParams(List.of(index)).build());
        client.loadCollection(LoadCollectionReq.builder().collectionName(collection).build());
    }

    private static List<Row> loadRows(String jdbcUrl) throws Exception {
        List<Row> rows = new ArrayList<>();
        String sql = "SELECT c.id, c.source_id, c.article_number, c.embedding_json, "
                + "s.title, s.source_url, s.region_code "
                + "FROM legal_source_chunk c JOIN legal_source s ON s.id = c.source_id "
                + "WHERE c.embedding_json IS NOT NULL";
        try (Connection connection = DriverManager.getConnection(jdbcUrl,
                LegalEmbeddingBackfillCli.env("MYSQL_USERNAME", "anyujiadev"),
                LegalEmbeddingBackfillCli.env("MYSQL_PASSWORD", ""));
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                rows.add(new Row(result.getLong(1), result.getLong(2), result.getString(3),
                        JSON.readValue(result.getString(4), double[].class), result.getString(5),
                        result.getString(6), result.getString(7)));
            }
        }
        return rows;
    }

    private static JsonObject toJson(Row row) {
        JsonObject json = new JsonObject();
        json.addProperty("chunk_id", row.chunkId());
        json.addProperty("source_id", row.sourceId());
        json.addProperty("article_number", nullable(row.articleNumber()));
        json.addProperty("title", nullable(row.title()));
        json.addProperty("source_url", nullable(row.sourceUrl()));
        json.addProperty("region", nullable(row.region()));
        json.addProperty("state", "ACTIVE");
        json.add("embedding", GSON.toJsonTree(toFloatArray(row.vector())));
        return json;
    }

    private static float[] toFloatArray(double[] vector) {
        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = (float) vector[i];
        }
        return result;
    }

    private static String nullable(String value) {
        return value == null ? "" : value;
    }

    private record Row(long chunkId, long sourceId, String articleNumber, double[] vector,
                       String title, String sourceUrl, String region) {
    }
}
