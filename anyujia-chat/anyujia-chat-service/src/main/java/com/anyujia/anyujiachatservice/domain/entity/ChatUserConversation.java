package com.anyujia.anyujiachatservice.domain.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ChatUserConversation {
    private Long userId;
    private Long conversationId;
    private Integer roleType;
    private Long otherUserId;
    private Long houseId;
    private Long housePublishId;
    private Long lastMessageId;
    private Long lastMessageSeq;
    private Long lastMessageTime;
    private Integer unreadCount;
    private Long lastReadMessageId;
    private Long lastReadConversationSeq;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
