package com.anyujia.anyujiahouseapi.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HousePublishAuditReqDTO {
    @NotNull
    @Pattern(regexp = "approve|reject")
    private String action;

    @Size(max = 500)
    private String remark;
}
