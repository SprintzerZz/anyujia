package com.anyujia.anyujialegalservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("legal_agent_task_outbox")
public class LegalAgentTaskOutbox {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private String eventType;
    private String payload;
    private Integer status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String claimToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
