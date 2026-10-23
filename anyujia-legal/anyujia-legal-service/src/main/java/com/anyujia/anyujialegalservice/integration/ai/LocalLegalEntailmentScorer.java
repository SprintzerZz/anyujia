package com.anyujia.anyujialegalservice.integration.ai;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.service.LegalResilientCallExecutor;
import java.net.http.HttpClient;
import java.time.Duration;
import com.anyujia.anyujialegalservice.service.LegalEntailmentScorer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Slf4j
public class LocalLegalEntailmentScorer implements LegalEntailmentScorer {
    private final LegalAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final LegalResilientCallExecutor callExecutor;
    private volatile boolean available = true;

    public LocalLegalEntailmentScorer(LegalAiProperties properties, ObjectMapper objectMapper,
                                      RestClient.Builder restClientBuilder) {
        this(properties, objectMapper, restClientBuilder, new LegalResilientCallExecutor());
    }

    public LocalLegalEntailmentScorer(LegalAiProperties properties, ObjectMapper objectMapper,
                                      RestClient.Builder restClientBuilder,
                                      LegalResilientCallExecutor callExecutor) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.callExecutor = callExecutor;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getLocalEntailment().getConnectTimeoutMillis()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getLocalEntailment().getReadTimeoutMillis()));
        this.restClient = restClientBuilder.clone().requestFactory(requestFactory).build();
    }

    @Override
    public Optional<Result> score(String premise, String hypothesis) {
        if (!available || premise == null || premise.isBlank() || hypothesis == null || hypothesis.isBlank()) {
            return Optional.empty();
        }
        return callExecutor.execute("legal-entailment", () -> {
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
            String modelType = root.path("model_type").asText("");
            if (!"nli".equalsIgnoreCase(modelType)) {
                available = false;
                log.warn("Legal entailment endpoint is not an NLI model (model_type={}); lexical fallback enabled",
                        modelType);
                return Optional.empty();
            }
            String label = root.path("label").asText(root.path("prediction").asText("UNKNOWN"));
            double score = root.path("score").asDouble(root.path("entailment").asDouble(0D));
            if (label.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new Result(label.toUpperCase(), Math.max(0D, Math.min(1D, score))));
        }, this::markUnavailable);
    }

    private Optional<Result> markUnavailable() {
        available = false;
        log.warn("Legal entailment model unavailable; lexical grounding fallback remains active");
        return Optional.empty();
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
