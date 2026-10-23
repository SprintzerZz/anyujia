package com.anyujia.anyujialegalservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("legal_agent_memory_snapshot")
public class LegalAgentMemorySnapshot {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long chatConversationId;
    private Long snapshotVersion;
    private String summary;
    private String factsJson;
    private String contextHash;
    private LocalDateTime lastMessageAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
