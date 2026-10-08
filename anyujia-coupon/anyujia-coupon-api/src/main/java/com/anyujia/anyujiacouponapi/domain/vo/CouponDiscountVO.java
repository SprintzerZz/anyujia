package com.anyujia.anyujiacouponapi.domain.vo;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 服务费订单锁券结果。
 */
@Data
public class CouponDiscountVO {

    private Long userCouponId;
    private Long orderId;
    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal payableAmount;
}
