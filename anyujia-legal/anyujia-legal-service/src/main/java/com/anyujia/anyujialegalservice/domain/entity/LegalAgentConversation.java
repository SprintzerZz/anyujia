package com.anyujia.anyujialegalservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("legal_agent_conversation")
public class LegalAgentConversation {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long chatConversationId;
    private Long landlordConversationId;
    private Long contractId;
    private Long assessmentJobId;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
