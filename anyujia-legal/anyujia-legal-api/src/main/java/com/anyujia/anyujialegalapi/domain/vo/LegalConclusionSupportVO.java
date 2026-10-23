package com.anyujia.anyujialegalapi.domain.vo;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/** Per-conclusion grounding assessment returned with an agent answer. */
@Data
public class LegalConclusionSupportVO {
    private String conclusion;
    private BigDecimal supportScore;
    private String supportLevel;
    private List<Integer> citationIndexes;
    private String reason;
    private String supportMethod;
}
