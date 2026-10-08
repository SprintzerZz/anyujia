package com.anyujia.anyujiacouponservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.anyujia.anyujiacouponapi.constants.CouponConstants;
import com.anyujia.anyujiacouponservice.domain.entity.CouponTemplate;
import org.junit.jupiter.api.Test;

class CouponDiscountCalculatorTest {

    private final CouponDiscountCalculator calculator = new CouponDiscountCalculator();

    @Test
    void fixedCouponDoesNotMakePayableAmountNegative() {
        CouponTemplate template = template(CouponConstants.TYPE_FIXED);
        template.setDiscountAmount(new BigDecimal("20.00"));

        assertThat(calculator.calculate(template, new BigDecimal("10.00")))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void thresholdCouponRequiresMinimumServiceFee() {
        CouponTemplate template = template(CouponConstants.TYPE_THRESHOLD);
        template.setThresholdAmount(new BigDecimal("100.00"));
        template.setDiscountAmount(new BigDecimal("20.00"));

        assertThat(calculator.calculate(template, new BigDecimal("99.99")))
                .isEqualByComparingTo("0.00");
        assertThat(calculator.calculate(template, new BigDecimal("100.00")))
                .isEqualByComparingTo("20.00");
    }

    @Test
    void discountCouponHonorsMaximumDiscount() {
        CouponTemplate template = template(CouponConstants.TYPE_DISCOUNT);
        template.setDiscountRate(new BigDecimal("0.80"));
        template.setMaxDiscountAmount(new BigDecimal("30.00"));

        assertThat(calculator.calculate(template, new BigDecimal("200.00")))
                .isEqualByComparingTo("30.00");
    }

    private CouponTemplate template(int type) {
        CouponTemplate template = new CouponTemplate();
        template.setType(type);
        return template;
    }
}
