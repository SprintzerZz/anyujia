package com.anyujia.anyujiachatapi.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentMessageReqDTO {
    @NotNull
    private Long chatConversationId;
    @NotBlank
    private String content;
    /** Stable delivery key supplied by Legal; retries must return the same message. */
    @Size(max = 64)
    private String requestId;
}
