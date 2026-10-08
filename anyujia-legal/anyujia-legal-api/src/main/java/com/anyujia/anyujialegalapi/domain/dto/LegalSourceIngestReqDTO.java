package com.anyujia.anyujialegalapi.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LegalSourceIngestReqDTO {
    @NotBlank
    @Size(max = 255)
    private String title;
    @NotBlank
    @Size(max = 255)
    private String issuer;
    @NotBlank
    @Size(max = 32)
    private String regionCode;
    @NotBlank
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
    private String effectiveFrom;
    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$")
    private String effectiveTo;
    @NotBlank
    @Pattern(regexp = "^https://.*")
    @Size(max = 1000)
    private String sourceUrl;
    @NotBlank
    @Size(max = 1000000)
    private String fullText;
}
