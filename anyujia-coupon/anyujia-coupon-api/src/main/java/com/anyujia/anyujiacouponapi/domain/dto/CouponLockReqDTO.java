package com.anyujia.anyujiacouponapi.domain.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 服务费订单锁券请求。
 */
@Data
public class CouponLockReqDTO {

    @NotNull(message = "用户优惠券ID不能为空")
    private Long userCouponId;

    @NotNull(message = "服务费订单ID不能为空")
    private Long orderId;

    @NotNull(message = "平台服务费不能为空")
    @DecimalMin(value = "0.01", message = "平台服务费必须大于0")
    private BigDecimal serviceFeeAmount;

    private String provinceCode;

    private String cityCode;

    private String districtCode;
}
