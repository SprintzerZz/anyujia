package com.anyujia.anyujialegalapi.domain.vo;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LegalCitationVO {
    Integer index;
    Long chunkId;
    Long sourceId;
    String title;
    String articleNumber;
    String quote;
    String sourceUrl;
}
