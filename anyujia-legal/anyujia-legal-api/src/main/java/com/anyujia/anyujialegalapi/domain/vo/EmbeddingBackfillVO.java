package com.anyujia.anyujialegalapi.domain.vo;

import lombok.Data;

@Data
public class EmbeddingBackfillVO {
    private Integer selectedCount;
    private Integer embeddedCount;
    private Integer failedCount;
    private Long remainingCount;
}
