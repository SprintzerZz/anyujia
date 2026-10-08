package com.anyujia.anyujiacouponservice.mq.event;

import java.io.Serializable;
import java.util.List;

public record CouponGrantBatchEvent(
        String batchId,
        Long taskId,
        Long templateId,
        Integer userType,
        List<Long> userIds,
        long receivedAtEpochMillis) implements Serializable {
}
