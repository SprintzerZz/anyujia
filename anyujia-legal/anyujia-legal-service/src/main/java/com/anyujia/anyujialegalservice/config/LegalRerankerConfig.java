package com.anyujia.anyujialegalservice.config;

import com.anyujia.anyujialegalservice.integration.ai.BgeLegalReranker;
import com.anyujia.anyujialegalservice.integration.ai.NoopLegalReranker;
import com.anyujia.anyujialegalservice.service.LegalReranker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class LegalRerankerConfig {

    @Bean
    @ConditionalOnProperty(prefix = "legal.ai.local-reranker", name = "enabled", havingValue = "true")
    public LegalReranker bgeReranker(LegalAiProperties properties, ObjectMapper objectMapper,
                                     RestClient.Builder restClientBuilder) {
        return new BgeLegalReranker(properties, objectMapper, restClientBuilder);
    }

    @Bean
    @ConditionalOnMissingBean(LegalReranker.class)
    public LegalReranker noopReranker() {
        return new NoopLegalReranker();
    }
}
