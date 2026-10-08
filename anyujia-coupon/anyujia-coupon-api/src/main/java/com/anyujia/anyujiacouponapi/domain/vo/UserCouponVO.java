package com.anyujia.anyujiacouponapi.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户持有的平台服务费优惠券。
 */
@Data
public class UserCouponVO {

    private Long id;
    private Long templateId;
    private Integer userType;
    private String name;
    private Integer type;
    private Integer scopeType;
    private String provinceCode;
    private String cityCode;
    private String districtCode;
    private String policyCode;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountRate;
    private BigDecimal maxDiscountAmount;
    private Integer status;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private LocalDateTime receivedAt;
}
