package com.anyujia.anyujialegalservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("legal_contract")
public class LegalContract {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long conversationId;
    private String objectKey;
    private String originalFilename;
    private String sha256;
    private String extractedText;
    private String documentType;
    private Integer pageCount;
    private String locatorPrecision;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
