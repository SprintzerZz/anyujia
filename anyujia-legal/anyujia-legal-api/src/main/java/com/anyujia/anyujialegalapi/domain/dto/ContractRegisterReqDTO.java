package com.anyujia.anyujialegalapi.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContractRegisterReqDTO {
    @NotNull
    private Long conversationId;
    @NotBlank
    @Size(max = 512)
    private String objectKey;
    @NotBlank
    @Size(max = 255)
    private String originalFilename;
    @NotBlank
    @Pattern(regexp = "(?i)^[a-f0-9]{64}$")
    private String sha256;
    @NotBlank
    @Size(max = 200000)
    private String extractedText;
}
