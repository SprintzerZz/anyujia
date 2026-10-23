package com.anyujia.anyujialegalservice.integration.ai;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import com.anyujia.anyujialegalservice.service.LegalReranker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

@Slf4j
public class BgeLegalReranker implements LegalReranker {
    private final LegalAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private volatile boolean available = true;

    public BgeLegalReranker(LegalAiProperties properties, ObjectMapper objectMapper,
                            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public List<LawPassage> rerank(String query, List<LawPassage> candidates) {
        if (query == null || query.isBlank() || candidates == null || candidates.size() < 2) {
            return candidates == null ? List.of() : candidates;
        }
        try {
            List<String> documents = candidates.stream().map(LawPassage::text).toList();
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("query", query);
            request.put("documents", documents);
            String body = restClient.post()
                    .uri(URI.create(properties.getLocalReranker().getEndpoint()))
                    .body(request)
                    .retrieve()
                    .body(String.class);
            JsonNode results = objectMapper.readTree(body).path("results");
            if (!results.isArray() || results.isEmpty()) {
                return candidates;
            }
            Map<Integer, Double> scores = new LinkedHashMap<>();
            for (JsonNode item : results) {
                int index = item.path("index").asInt(-1);
                double score = item.path("score").asDouble();
                if (index >= 0 && index < candidates.size()) {
                    scores.put(index, score);
                }
            }
            if (scores.isEmpty()) {
                return candidates;
            }
            List<Integer> order = scores.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .toList();
            List<LawPassage> reordered = new ArrayList<>(candidates.size());
            Set<Integer> seen = new LinkedHashSet<>(order);
            for (Integer index : order) {
                reordered.add(candidates.get(index));
            }
            for (int i = 0; i < candidates.size(); i++) {
                if (!seen.contains(i)) {
                    reordered.add(candidates.get(i));
                }
            }
            return reordered;
        } catch (Exception ex) {
            available = false;
            log.warn("Local BGE reranker failed; deterministic rerank order remains: {}", ex.getMessage());
            return candidates;
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
