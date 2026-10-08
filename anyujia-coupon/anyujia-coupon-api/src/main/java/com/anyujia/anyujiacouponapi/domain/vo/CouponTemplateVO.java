package com.anyujia.anyujiacouponapi.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 平台服务费优惠券模板。
 */
@Data
public class CouponTemplateVO {

    private Long id;
    private String name;
    private Integer type;
    private Integer applicableUserType;
    private Integer scopeType;
    private String provinceCode;
    private String cityCode;
    private String districtCode;
    private String policyCode;
    private Integer grantMode;
    private Integer receiveLimit;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountRate;
    private BigDecimal maxDiscountAmount;
    private Integer totalStock;
    private Integer availableStock;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private Integer status;
    private LocalDateTime createdAt;
}
