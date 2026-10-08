package com.anyujia.anyujiacouponservice.mq.event;

public record CouponExpireEvent(
        Long userCouponId,
        Long userId,
        Integer userType,
        Long templateId,
        Long expireAtEpochMillis
) {
}
