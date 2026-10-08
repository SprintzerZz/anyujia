package com.anyujia.anyujialegalservice.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.domain.model.RiskFinding;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class DashScopeRiskAnalysisEnhancerTest {
    @Test
    void keepsDeterministicFindingsWhenCredentialIsMissing() {
        LegalAiProperties properties = new LegalAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("");
        DashScopeRiskAnalysisEnhancer enhancer = new DashScopeRiskAnalysisEnhancer(
                properties, new ObjectMapper(), RestClient.builder(), new DashScopeCallGuard(properties));
        List<RiskFinding> input = List.of(new RiskFinding("R1", "HIGH", "title", "conclusion",
                "quote", "law", "suggestion", 90, false));

        List<RiskFinding> result = enhancer.enhance("untrusted contract text", input);

        assertThat(result).isSameAs(input);
        assertThat(result.get(0).isAiEnhanced()).isFalse();
    }
}
