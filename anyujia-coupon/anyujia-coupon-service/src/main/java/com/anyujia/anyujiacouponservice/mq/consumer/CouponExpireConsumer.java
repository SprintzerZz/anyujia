package com.anyujia.anyujiacouponservice.mq.consumer;

import java.time.Duration;

import com.anyujia.anyujiacouponservice.mq.event.CouponExpireEvent;
import com.anyujia.anyujiacouponservice.service.UserCouponExpireService;
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
        topic = "${coupon.rocketmq.expire-topic:coupon-expire-topic}",
        consumerGroup = "${coupon.rocketmq.expire-consumer-group:coupon-expire-consumer-group}",
        maxReconsumeTimes = 16)
public class CouponExpireConsumer implements RocketMQListener<CouponExpireEvent> {

    private static final String DONE_KEY = "coupon:mq:expire:done:";
    private static final String PROCESSING_KEY = "coupon:mq:expire:processing:";
    private static final Duration IDEMPOTENT_TTL = Duration.ofDays(90);
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(10);

    private final UserCouponExpireService expireService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(CouponExpireEvent event) {
        String doneKey = DONE_KEY + event.userCouponId();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(doneKey))) {
            return;
        }
        String processingKey = PROCESSING_KEY + event.userCouponId();
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(processingKey, "1", PROCESSING_TTL))) {
            return;
        }
        try {
            expireService.expire(event);
            redisTemplate.opsForValue().set(doneKey, "1", IDEMPOTENT_TTL);
        } finally {
            redisTemplate.delete(processingKey);
        }
    }
}
