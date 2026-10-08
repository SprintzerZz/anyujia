package com.anyujia.anyujialegalapi.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class RiskItemVO {
    private String riskCode;
    private String level;
    private String title;
    private String conclusion;
    private String evidenceQuote;
    private String legalBasis;
    private String suggestion;
    private Integer confidence;
    private Boolean aiEnhanced;
    private List<EvidenceRefVO> evidence;
}
