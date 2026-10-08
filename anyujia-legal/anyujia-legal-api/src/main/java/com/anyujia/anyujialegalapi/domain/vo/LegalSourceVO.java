package com.anyujia.anyujialegalapi.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class LegalSourceVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceId;
    private String title;
    private Integer chunkCount;
    private Integer embeddedChunkCount;
}
