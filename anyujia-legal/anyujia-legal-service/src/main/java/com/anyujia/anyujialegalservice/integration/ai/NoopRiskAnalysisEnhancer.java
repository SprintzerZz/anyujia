package com.anyujia.anyujialegalservice.integration.ai;

import com.anyujia.anyujialegalservice.domain.model.RiskFinding;
import com.anyujia.anyujialegalservice.service.RiskAnalysisEnhancer;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "legal.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopRiskAnalysisEnhancer implements RiskAnalysisEnhancer {
    @Override
    public List<RiskFinding> enhance(String contractText, List<RiskFinding> deterministicFindings) {
        return deterministicFindings;
    }
}
