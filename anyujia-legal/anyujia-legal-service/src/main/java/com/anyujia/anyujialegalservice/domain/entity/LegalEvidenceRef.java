package com.anyujia.anyujialegalservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("legal_evidence_ref")
public class LegalEvidenceRef {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long riskItemId;
    private String evidenceType;
    private String sourceId;
    private Integer pageNumber;
    private Integer paragraphIndex;
    private String clauseNumber;
    private String locatorPrecision;
    private String quoteText;
    private String sourceUrl;
    private LocalDateTime createdAt;
}
