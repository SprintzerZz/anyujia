package com.anyujia.anyujialegalapi.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LegalAgentAnswerReqDTO {
    @NotNull
    private Long chatConversationId;
    @NotBlank
    private String query;
    @Size(max = 32)
    private String regionCode;
    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "asOfDate 必须为 yyyy-MM-dd")
    private String asOfDate;
}
