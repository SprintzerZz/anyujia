package com.anyujia.anyujiacouponapi.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 服务费订单优惠券状态变更请求。
 */
@Data
public class CouponOrderActionReqDTO {

    @NotNull(message = "服务费订单ID不能为空")
    private Long orderId;
}
