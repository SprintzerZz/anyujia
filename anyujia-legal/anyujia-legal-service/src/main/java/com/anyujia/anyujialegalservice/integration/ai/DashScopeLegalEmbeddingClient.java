package com.anyujia.anyujialegalservice.integration.ai;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.service.LegalEmbeddingClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

@Slf4j
public class DashScopeLegalEmbeddingClient implements LegalEmbeddingClient {
    private final LegalAiProperties properties;
    private final ObjectMapper objectMapper;
    private final LegalEmbeddingCache cache;
    private final DashScopeCallGuard callGuard;
    private final RestClient restClient;

    public DashScopeLegalEmbeddingClient(LegalAiProperties properties, ObjectMapper objectMapper,
                                         RestClient.Builder restClientBuilder, LegalEmbeddingCache cache,
                                         DashScopeCallGuard callGuard) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.cache = cache;
        this.callGuard = callGuard;
        this.restClient = DashScopeRestClientFactory.create(restClientBuilder, properties);
    }

    DashScopeLegalEmbeddingClient(LegalAiProperties properties, ObjectMapper objectMapper,
                                  LegalEmbeddingCache cache, DashScopeCallGuard callGuard,
                                  RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.cache = cache;
        this.callGuard = callGuard;
        this.restClient = restClient;
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
        if (properties.getApiKey().isBlank()) {
            log.debug("Legal embedding skipped because no API key is configured");
            return results;
        }

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
        if (missing.isEmpty() || !callGuard.allowRequest(DashScopeCallGuard.EMBEDDING)) {
            log.debug("Legal embedding batch completed without remote call: inputs={}, cacheMisses={}",
                    texts.size(), missing.size());
            return results;
        }

        List<String> uniqueTexts = new ArrayList<>(missing.keySet());
        log.debug("Legal embedding remote batch: inputs={}, uniqueMisses={}, batchSize={}",
                texts.size(), uniqueTexts.size(), properties.getEmbedding().getBatchSize());
        int batchSize = properties.getEmbedding().getBatchSize();
        for (int start = 0; start < uniqueTexts.size(); start += batchSize) {
            List<String> batch = uniqueTexts.subList(start, Math.min(start + batchSize, uniqueTexts.size()));
            if (!callGuard.allowRequest(DashScopeCallGuard.EMBEDDING)) {
                break;
            }
            Map<String, double[]> vectors = requestBatch(batch);
            vectors.forEach((text, vector) -> {
                cache.put(namespace(), text, vector);
                missing.get(text).forEach(index -> results.set(index, Optional.of(vector)));
            });
        }
        return results;
    }

    private String namespace() {
        return properties.getEmbedding().getModel() + ':' + properties.getEmbedding().getDimensions();
    }

    private Map<String, double[]> requestBatch(List<String> texts) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", properties.getEmbedding().getModel());
            request.put("input", texts);
            request.put("dimensions", properties.getEmbedding().getDimensions());
            String body = restClient.post().uri("/embeddings")
                    .header("Authorization", "Bearer " + properties.getApiKey())
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
            if (result.size() == texts.size()) {
                callGuard.recordSuccess(DashScopeCallGuard.EMBEDDING);
            } else {
                callGuard.recordFailure(DashScopeCallGuard.EMBEDDING);
            }
            log.debug("Legal embedding response parsed: requested={}, returned={}", texts.size(), result.size());
            return result;
        } catch (Exception ex) {
            callGuard.recordFailure(DashScopeCallGuard.EMBEDDING);
            log.warn("Legal embedding batch failed; lexical retrieval remains available: {}", ex.getMessage());
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
}
