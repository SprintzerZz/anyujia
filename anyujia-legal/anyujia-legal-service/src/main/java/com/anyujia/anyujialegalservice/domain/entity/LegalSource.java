package com.anyujia.anyujialegalservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("legal_source")
public class LegalSource {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String title;
    private String issuer;
    private String regionCode;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String sourceUrl;
    private String contentHash;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
