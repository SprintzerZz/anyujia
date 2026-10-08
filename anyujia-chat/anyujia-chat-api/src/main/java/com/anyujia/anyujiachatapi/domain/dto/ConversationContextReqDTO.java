package com.anyujia.anyujiachatapi.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConversationContextReqDTO {
    @NotNull
    private Long sessionId;
}
