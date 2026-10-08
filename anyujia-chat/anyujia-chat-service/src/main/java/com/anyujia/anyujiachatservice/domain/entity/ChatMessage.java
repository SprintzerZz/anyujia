package com.anyujia.anyujiachatservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String clientMessageId;
    private Long conversationId;
    private Long conversationSeq;
    private Long senderId;
    private Long recipientId;
    private Integer type;
    private String content;
    private Integer status;
    private Integer visited;
    private LocalDateTime createdAt;
}
