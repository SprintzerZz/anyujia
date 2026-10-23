package com.anyujia.anyujialegalservice.integration.ai;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.service.LegalEmbeddingClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

@Slf4j
public class BgeLegalEmbeddingClient implements LegalEmbeddingClient {
    private final LegalAiProperties properties;
    private final ObjectMapper objectMapper;
    private final LegalEmbeddingCache cache;
    private final RestClient restClient;

    public BgeLegalEmbeddingClient(LegalAiProperties properties, ObjectMapper objectMapper,
                                   LegalEmbeddingCache cache, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.cache = cache;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public Optional<double[]> embed(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<Optional<double[]>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<Optional<double[]>> results = new ArrayList<>(Collections.nCopies(texts.size(), Optional.empty()));
        Map<String, List<Integer>> missing = new LinkedHashMap<>();
        for (int index = 0; index < texts.size(); index++) {
            String text = texts.get(index);
            if (text == null || text.isBlank()) {
                continue;
            }
            Optional<double[]> cached = cache.get(namespace(), text);
            if (cached.isPresent()) {
                results.set(index, cached);
            } else {
                missing.computeIfAbsent(text, ignored -> new ArrayList<>()).add(index);
            }
        }
        if (missing.isEmpty()) {
            return results;
        }
        List<String> uniqueTexts = new ArrayList<>(missing.keySet());
        int batchSize = properties.getEmbedding().getBatchSize();
        for (int start = 0; start < uniqueTexts.size(); start += batchSize) {
            List<String> batch = uniqueTexts.subList(start, Math.min(start + batchSize, uniqueTexts.size()));
            Map<String, double[]> vectors = requestBatch(batch);
            vectors.forEach((text, vector) -> {
                cache.put(namespace(), text, vector);
                missing.get(text).forEach(index -> results.set(index, Optional.of(vector)));
            });
        }
        return results;
    }

    private Map<String, double[]> requestBatch(List<String> texts) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", properties.getLocalEmbedding().getModel());
            request.put("input", texts);
            String body = restClient.post()
                    .uri(URI.create(properties.getLocalEmbedding().getEndpoint()))
                    .body(request)
                    .retrieve()
                    .body(String.class);
            JsonNode data = objectMapper.readTree(body).path("data");
            Map<String, double[]> result = new LinkedHashMap<>();
            if (data.isArray()) {
                for (JsonNode item : data) {
                    int index = item.path("index").asInt(-1);
                    JsonNode values = item.path("embedding");
                    if (index >= 0 && index < texts.size() && values.isArray() && !values.isEmpty()) {
                        result.put(texts.get(index), vector(values));
                    }
                }
            }
            return result;
        } catch (Exception ex) {
            log.warn("Local BGE embedding batch failed; lexical retrieval remains available: {}",
                    ex.getMessage());
            return Map.of();
        }
    }

    private double[] vector(JsonNode values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i).asDouble();
        }
        return result;
    }

    private String namespace() {
        return properties.getLocalEmbedding().getModel() + ':' + properties.getLocalEmbedding().getDimensions();
    }
}
