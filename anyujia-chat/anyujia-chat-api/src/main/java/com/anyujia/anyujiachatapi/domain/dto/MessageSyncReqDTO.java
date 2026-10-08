package com.anyujia.anyujiachatapi.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MessageSyncReqDTO {
    @NotNull
    private Long sessionId;
    private String afterConversationSeq;
    @Deprecated
    private String afterMessageId;
    private Integer count = 100;
}
