package com.anyujia.anyujiachatservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_conversation")
public class ChatConversation {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantUserId;
    private Long landlordUserId;
    private Long houseId;
    private Long housePublishId;
    private Long lastMessageId;
    private Long lastMessageSeq;
    private Long nextMessageSeq;
    private Long lastMessageTime;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
