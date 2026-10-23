package com.anyujia.anyujialegalservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("legal_agent_task")
public class LegalAgentTask {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String requestId;
    private Long tenantId;
    private Long chatConversationId;
    private String queryText;
    private String regionCode;
    private LocalDate asOfDate;
    private String status;
    private Integer attemptCount;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private String answerJson;
    private String contextHash;
    private String contextStatus;
    private Boolean contextConflict;
    private Boolean reviewRequired;
    private String evidenceManifestJson;
    private Integer chatDeliveryState;
    private LocalDateTime chatDeliveryClaimedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
