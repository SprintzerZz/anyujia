package com.anyujia.anyujialegalapi.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ContractVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;
    private String originalFilename;
    private String sha256;
    private String documentType;
    private Integer pageCount;
    private String locatorPrecision;
    private Integer status;
    private Long createdAt;
}
