package com.anyujia.anyujialegalapi.domain.vo;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LegalPassageVO {
    Long chunkId;
    Long sourceId;
    String title;
    String articleNumber;
    String text;
    String sourceUrl;
    Double score;
}
