package com.anyujia.anyujialegalapi.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LegalAgentTaskSubmitReqDTO extends LegalAgentAnswerReqDTO {
    @NotBlank
    @Size(max = 64)
    private String requestId;
}
