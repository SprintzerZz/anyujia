package com.anyujia.anyujialegalservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("legal_source_chunk")
public class LegalSourceChunk {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long sourceId;
    private Integer chunkIndex;
    private String articleNumber;
    private String chunkText;
    private String embeddingJson;
}
