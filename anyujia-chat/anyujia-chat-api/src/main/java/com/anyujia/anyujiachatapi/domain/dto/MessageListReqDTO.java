package com.anyujia.anyujiachatapi.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MessageListReqDTO {
    @NotNull
    private Long sessionId;
    private String lastConversationSeq;
    @Deprecated
    private String lastMessageId;
    private Integer count = 20;
    private Boolean needCurMessage = false;
}
