package com.anyujia.anyujiacouponapi.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 平台服务费优惠券模板创建请求。
 */
@Data
public class CouponTemplateCreateReqDTO {

    @Min(value = 0, message = "适用用户类型只能是0、1或2")
    @Max(value = 2, message = "适用用户类型只能是0、1或2")
    private Integer applicableUserType = 0;

    @Min(value = 1, message = "适用范围只能是平台、区域或国补")
    @Max(value = 3, message = "适用范围只能是平台、区域或国补")
    private Integer scopeType = 1;

    private String provinceCode;

    private String cityCode;

    private String districtCode;

    private String policyCode;

    @Min(value = 1, message = "发放方式只能是公开领取或名单发放")
    @Max(value = 2, message = "发放方式只能是公开领取或名单发放")
    private Integer grantMode = 1;

    @Min(value = 1, message = "单用户限领次数必须大于0")
    private Integer receiveLimit = 1;

    @NotBlank(message = "优惠券名称不能为空")
    private String name;

    @NotNull(message = "优惠券类型不能为空")
    private Integer type;

    @DecimalMin(value = "0.01", message = "门槛金额必须大于0")
    private BigDecimal thresholdAmount;

    @DecimalMin(value = "0.01", message = "优惠金额必须大于0")
    private BigDecimal discountAmount;

    @DecimalMin(value = "0.01", message = "折扣率必须大于0")
    @DecimalMax(value = "0.99", message = "折扣率必须小于1")
    private BigDecimal discountRate;

    @DecimalMin(value = "0.01", message = "最高优惠金额必须大于0")
    private BigDecimal maxDiscountAmount;

    @NotNull(message = "库存不能为空")
    @Min(value = 1, message = "库存必须大于0")
    private Integer stock;

    @NotNull(message = "生效时间不能为空")
    private LocalDateTime validStartTime;

    @NotNull(message = "失效时间不能为空")
    @Future(message = "失效时间必须晚于当前时间")
    private LocalDateTime validEndTime;
}
