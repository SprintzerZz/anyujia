package com.anyujia.anyujiachatapi.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MessageSendReqDTO {
    @NotNull
    private Long sessionId;
    @NotBlank
    @Size(max = 64)
    private String clientMessageId;
    @NotNull
    private Integer type;
    @NotNull
    @Size(max = 1024)
    private String content;
}
