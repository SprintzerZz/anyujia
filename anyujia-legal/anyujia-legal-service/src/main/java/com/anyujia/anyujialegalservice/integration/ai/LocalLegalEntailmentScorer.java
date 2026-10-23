package com.anyujia.anyujialegalservice.integration.ai;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.service.LegalEntailmentScorer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

@Slf4j
public class LocalLegalEntailmentScorer implements LegalEntailmentScorer {
    private final LegalAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private volatile boolean available = true;

    public LocalLegalEntailmentScorer(LegalAiProperties properties, ObjectMapper objectMapper,
                                      RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public Optional<Result> score(String premise, String hypothesis) {
        if (!available || premise == null || premise.isBlank() || hypothesis == null || hypothesis.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("premise", premise);
            request.put("hypothesis", hypothesis);
            request.put("model", properties.getLocalEntailment().getModel());
            String body = restClient.post()
                    .uri(URI.create(properties.getLocalEntailment().getEndpoint()))
                    .body(request)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            String label = root.path("label").asText(root.path("prediction").asText("UNKNOWN"));
            double score = root.path("score").asDouble(root.path("entailment").asDouble(0D));
            if (label.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new Result(label.toUpperCase(), Math.max(0D, Math.min(1D, score))));
        } catch (Exception ex) {
            available = false;
            log.warn("Legal entailment model unavailable; lexical grounding fallback remains active: {}",
                    ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
