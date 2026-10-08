package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.domain.model.RiskFinding;
import java.util.List;

public interface RiskAnalysisEnhancer {
    List<RiskFinding> enhance(String contractText, List<RiskFinding> deterministicFindings);

    default List<RiskFinding> enhance(String contractText, String supplementalContext,
                                      List<RiskFinding> deterministicFindings) {
        return enhance(contractText, deterministicFindings);
    }
}
