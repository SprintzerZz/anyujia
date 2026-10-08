package com.anyujia.anyujialegalservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("legal_assessment_context_snapshot")
public class LegalAssessmentContextSnapshot {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long jobId;
    private Long contractId;
    private Long conversationId;
    private Long tenantId;
    private String regionCode;
    private LocalDate asOfDate;
    private String contractHash;
    private Integer contractBlockCount;
    private String chatHash;
    private Integer chatMessageCount;
    private Integer chatMessageLimit;
    private String productHash;
    private Long housePublishId;
    private String retrievalHash;
    private Integer retrievalQueryCount;
    private Integer lawPassageCount;
    private String contextHash;
    private LocalDateTime createdAt;
}
