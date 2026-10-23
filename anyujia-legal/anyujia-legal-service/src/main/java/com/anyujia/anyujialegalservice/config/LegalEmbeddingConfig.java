package com.anyujia.anyujialegalservice.config;

import com.anyujia.anyujialegalservice.integration.ai.BgeLegalEmbeddingClient;
import com.anyujia.anyujialegalservice.integration.ai.DashScopeCallGuard;
import com.anyujia.anyujialegalservice.integration.ai.DashScopeLegalEmbeddingClient;
import com.anyujia.anyujialegalservice.integration.ai.LegalEmbeddingCache;
import com.anyujia.anyujialegalservice.integration.ai.NoopLegalEmbeddingClient;
import com.anyujia.anyujialegalservice.service.LegalEmbeddingClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class LegalEmbeddingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "legal.ai.local-embedding", name = "enabled", havingValue = "true")
    public LegalEmbeddingClient bgeEmbeddingClient(LegalAiProperties properties, ObjectMapper objectMapper,
                                                   LegalEmbeddingCache cache,
                                                   RestClient.Builder restClientBuilder) {
        return new BgeLegalEmbeddingClient(properties, objectMapper, cache, restClientBuilder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "legal.ai.embedding", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(LegalEmbeddingClient.class)
    public LegalEmbeddingClient dashScopeEmbeddingClient(LegalAiProperties properties, ObjectMapper objectMapper,
                                                         RestClient.Builder restClientBuilder,
                                                         LegalEmbeddingCache cache, DashScopeCallGuard callGuard) {
        return new DashScopeLegalEmbeddingClient(properties, objectMapper, restClientBuilder, cache, callGuard);
    }

    @Bean
    @ConditionalOnMissingBean(LegalEmbeddingClient.class)
    public LegalEmbeddingClient noopEmbeddingClient() {
        return new NoopLegalEmbeddingClient();
    }
}
