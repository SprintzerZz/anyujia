package com.anyujia.anyujiachatservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_conversation_member")
public class ChatConversationMember {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long userId;
    private Integer roleType;
    private Integer unreadCount;
    private Long lastReadMessageId;
    private Long lastReadConversationSeq;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
