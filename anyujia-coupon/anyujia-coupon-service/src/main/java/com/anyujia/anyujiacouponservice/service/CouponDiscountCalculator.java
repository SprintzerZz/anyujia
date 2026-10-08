package com.anyujia.anyujiacouponservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.anyujia.anyujiacouponapi.constants.CouponConstants;
import com.anyujia.anyujiacouponservice.domain.entity.CouponTemplate;
import org.springframework.stereotype.Component;

/**
 * 平台服务费优惠金额计算器。
 */
@Component
public class CouponDiscountCalculator {

    public BigDecimal calculate(CouponTemplate template, BigDecimal serviceFeeAmount) {
        BigDecimal discount;
        switch (template.getType()) {
            case CouponConstants.TYPE_FIXED -> discount = template.getDiscountAmount();
            case CouponConstants.TYPE_THRESHOLD -> {
                if (serviceFeeAmount.compareTo(template.getThresholdAmount()) < 0) {
                    return BigDecimal.ZERO;
                }
                discount = template.getDiscountAmount();
            }
            case CouponConstants.TYPE_DISCOUNT -> {
                BigDecimal rate = template.getDiscountRate();
                discount = serviceFeeAmount.subtract(serviceFeeAmount.multiply(rate));
                if (template.getMaxDiscountAmount() != null) {
                    discount = discount.min(template.getMaxDiscountAmount());
                }
            }
            default -> throw new ServiceException("不支持的优惠券类型", ResultCode.INVALID_PARA.getCode());
        }
        return discount.min(serviceFeeAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.DOWN);
    }
}
