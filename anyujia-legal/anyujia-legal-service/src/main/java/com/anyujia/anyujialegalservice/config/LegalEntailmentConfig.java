package com.anyujia.anyujialegalservice.config;

import com.anyujia.anyujialegalservice.integration.ai.LocalLegalEntailmentScorer;
import com.anyujia.anyujialegalservice.integration.ai.NoopLegalEntailmentScorer;
import com.anyujia.anyujialegalservice.service.LegalEntailmentScorer;
import com.anyujia.anyujialegalservice.service.LegalResilientCallExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class LegalEntailmentConfig {
    @Bean
    @ConditionalOnProperty(prefix = "legal.ai.local-entailment", name = "enabled", havingValue = "true")
    public LegalEntailmentScorer localLegalEntailmentScorer(LegalAiProperties properties,
                                                            ObjectMapper objectMapper,
                                                            RestClient.Builder restClientBuilder,
                                                            LegalResilientCallExecutor callExecutor) {
        return new LocalLegalEntailmentScorer(properties, objectMapper, restClientBuilder, callExecutor);
    }

    @Bean
    @ConditionalOnMissingBean(LegalEntailmentScorer.class)
    public LegalEntailmentScorer noopLegalEntailmentScorer() {
        return new NoopLegalEntailmentScorer();
    }
}
