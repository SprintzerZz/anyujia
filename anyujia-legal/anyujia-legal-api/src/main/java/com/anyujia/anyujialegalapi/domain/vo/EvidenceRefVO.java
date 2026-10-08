package com.anyujia.anyujialegalapi.domain.vo;

import lombok.Data;

@Data
public class EvidenceRefVO {
    private String evidenceType;
    private String sourceId;
    private Integer pageNumber;
    private Integer paragraphIndex;
    private String clauseNumber;
    private String locatorPrecision;
    private String quoteText;
    private String sourceUrl;
}
