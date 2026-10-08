package com.anyujia.anyujialegalservice.domain.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RiskFinding {
    private String riskCode;
    private String level;
    private String title;
    private String conclusion;
    private String evidenceQuote;
    private String legalBasis;
    private String suggestion;
    private int confidence;
    private boolean aiEnhanced;
    private String evidenceType;
    private String evidenceSourceId;
    private Integer evidencePageNumber;
    private Integer evidenceParagraphIndex;
    private String evidenceLocatorPrecision;

    public RiskFinding(String riskCode, String level, String title, String conclusion,
                       String evidenceQuote, String legalBasis, String suggestion,
                       int confidence, boolean aiEnhanced) {
        this.riskCode = riskCode;
        this.level = level;
        this.title = title;
        this.conclusion = conclusion;
        this.evidenceQuote = evidenceQuote;
        this.legalBasis = legalBasis;
        this.suggestion = suggestion;
        this.confidence = confidence;
        this.aiEnhanced = aiEnhanced;
    }
}
