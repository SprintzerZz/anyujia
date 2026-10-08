package com.anyujia.anyujiacouponapi.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户领取优惠券请求。
 */
@Data
public class CouponReceiveReqDTO {

    @NotNull(message = "优惠券模板ID不能为空")
    private Long templateId;

    private String requestId;

    private String provinceCode;

    private String cityCode;

    private String districtCode;
}
