package com.anyujia.anyujialegalapi.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LegalAnswerReqDTO {
    @NotBlank
    @Size(max = 1000)
    private String query;
    @Size(max = 32)
    private String regionCode = "GLOBAL";
    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$")
    private String asOfDate;
    @Min(1)
    @Max(10)
    private Integer limit = 5;
}
