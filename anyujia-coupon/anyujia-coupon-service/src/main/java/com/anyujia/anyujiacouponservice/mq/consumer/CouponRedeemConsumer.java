package com.anyujia.anyujiacouponservice.mq.consumer;

import java.time.Duration;

import com.anyujia.anyujiacouponservice.exception.CouponStockConflictException;
import com.anyujia.anyujiacouponservice.mq.event.CouponRedeemEvent;
import com.anyujia.anyujiacouponservice.service.CouponReceiveCacheService;
import com.anyujia.anyujiacouponservice.service.CouponRedeemPersistenceService;
import com.anyujia.anyujiacouponservice.service.CouponRedeemPersistenceService.PersistResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@RocketMQMessageListener(
        topic = "${coupon.rocketmq.redeem-topic:coupon-redeem-topic}",
        consumerGroup = "${coupon.rocketmq.redeem-consumer-group:coupon-redeem-consumer-group}",
        maxReconsumeTimes = 16)
public class CouponRedeemConsumer implements RocketMQListener<CouponRedeemEvent> {

    private static final String IDEMPOTENT_KEY = "coupon:mq:redeem:done:";
    private static final String PROCESSING_KEY = "coupon:mq:redeem:processing:";
    private static final Duration IDEMPOTENT_TTL = Duration.ofDays(7);
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(10);

    private final CouponRedeemPersistenceService persistenceService;
    private final CouponReceiveCacheService couponReceiveCacheService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(CouponRedeemEvent event) {
        String idempotentKey = IDEMPOTENT_KEY + event.requestId();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(idempotentKey))) {
            return;
        }
        String processingKey = PROCESSING_KEY + event.requestId();
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(processingKey, "1", PROCESSING_TTL))) {
            return;
        }
        try {
            PersistResult result = persistenceService.persist(event);
            if (result == PersistResult.USER_ALREADY_RECEIVED) {
                couponReceiveCacheService.restoreStockKeepReceivedMarker(
                        event.templateId(), event.userType(), event.userId());
            }
            redisTemplate.opsForValue().set(idempotentKey, "1", IDEMPOTENT_TTL);
        } catch (CouponStockConflictException ex) {
            couponReceiveCacheService.invalidateReservation(
                    event.templateId(), event.userType(), event.userId());
            log.error("Coupon redeem rejected by database, requestId={}", event.requestId(), ex);
        } finally {
            redisTemplate.delete(processingKey);
        }
    }
}
