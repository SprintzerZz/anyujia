package com.anyujia.anyujialegalservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.time.LocalDate;
import lombok.Data;

@Data
@TableName("legal_assessment_job")
public class LegalAssessmentJob {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String requestId;
    private Long contractId;
    private Long tenantId;
    private String regionCode;
    private LocalDate asOfDate;
    private String status;
    private Integer attemptCount;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime nextRunAt;
    private String summary;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
