package com.anyujia.anyujialegalapi.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.util.List;
import lombok.Data;

@Data
public class AssessmentJobVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long jobId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;
    private String status;
    private String summary;
    private String errorMessage;
    private Long createdAt;
    private Long completedAt;
    private List<RiskItemVO> risks;
}
