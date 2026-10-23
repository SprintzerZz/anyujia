package com.anyujia.anyujialegalservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("legal_agent_stage_checkpoint")
public class LegalAgentStageCheckpoint {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private String stage;
    private String status;
    private Integer attemptCount;
    private String inputFingerprint;
    private String outputJson;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
