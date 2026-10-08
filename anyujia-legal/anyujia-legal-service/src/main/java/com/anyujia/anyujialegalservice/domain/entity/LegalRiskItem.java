package com.anyujia.anyujialegalservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("legal_risk_item")
public class LegalRiskItem {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long jobId;
    private String riskCode;
    private String riskLevel;
    private String title;
    private String conclusion;
    private String evidenceQuote;
    private String legalBasis;
    private String suggestion;
    private Integer confidence;
    private Boolean aiEnhanced;
    private LocalDateTime createdAt;
}
