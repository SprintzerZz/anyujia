package com.anyujia.anyujialegalapi.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssessmentStartReqDTO {
    @NotNull
    private Long contractId;
    @NotBlank
    @Size(max = 64)
    private String requestId;
    @Size(max = 32)
    private String regionCode = "GLOBAL";
    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$")
    private String asOfDate;
}
