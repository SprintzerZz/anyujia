package com.anyujia.anyujiacouponservice.mq.event;

import java.io.Serializable;
public record CouponRedeemEvent(
        String requestId,
        Long userId,
        Integer userType,
        Long templateId,
        long receivedAtEpochMillis) implements Serializable {
}
