package com.anyujia.anyujialegalapi.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LegalAgentReviewVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reviewId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    private String status;
    private String reason;
    private BigDecimal supportScore;
    private Integer attemptCount;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private String answerJson;
}
