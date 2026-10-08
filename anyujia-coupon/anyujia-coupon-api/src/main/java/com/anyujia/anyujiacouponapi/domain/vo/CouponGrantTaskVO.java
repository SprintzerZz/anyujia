package com.anyujia.anyujiacouponapi.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class CouponGrantTaskVO {

    private Long id;
    private Long templateId;
    private Integer userType;
    private Integer totalCount;
    private Integer successCount;
    private Integer failureCount;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
