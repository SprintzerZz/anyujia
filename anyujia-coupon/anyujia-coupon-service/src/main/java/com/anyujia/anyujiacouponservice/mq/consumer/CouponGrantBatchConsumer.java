package com.anyujia.anyujiacouponservice.mq.consumer;

import java.time.Duration;

import com.anyujia.anyujiacouponapi.constants.CouponConstants;
import com.anyujia.anyujiacouponservice.exception.CouponStockConflictException;
import com.anyujia.anyujiacouponservice.mapper.CouponGrantTaskMapper;
import com.anyujia.anyujiacouponservice.mq.event.CouponGrantBatchEvent;
import com.anyujia.anyujiacouponservice.service.CouponGrantBatchPersistenceService;
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
        topic = "${coupon.rocketmq.grant-topic:coupon-grant-topic}",
        consumerGroup = "${coupon.rocketmq.grant-consumer-group:coupon-grant-consumer-group}",
        maxReconsumeTimes = 16)
public class CouponGrantBatchConsumer implements RocketMQListener<CouponGrantBatchEvent> {

    private static final String DONE_KEY = "coupon:mq:grant:done:";
    private static final String PROCESSING_KEY = "coupon:mq:grant:processing:";
    private static final String TASK_USER_SET_KEY = "coupon:grant:task:%d:users";
    private static final Duration IDEMPOTENT_TTL = Duration.ofDays(7);
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(10);

    private final CouponGrantBatchPersistenceService persistenceService;
    private final CouponGrantTaskMapper couponGrantTaskMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(CouponGrantBatchEvent event) {
        String doneKey = DONE_KEY + event.batchId();
        if (redisTemplate.hasKey(doneKey)) {
            return;
        }
        String processingKey = PROCESSING_KEY + event.batchId();
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(processingKey, "1", PROCESSING_TTL))) {
            return;
        }
        try {
            persistenceService.persist(event);
            redisTemplate.opsForValue().set(doneKey, "1", IDEMPOTENT_TTL);
            redisTemplate.opsForSet().remove(TASK_USER_SET_KEY.formatted(event.taskId()),
                    (Object[]) event.userIds().stream().map(String::valueOf).toArray(String[]::new));
        } catch (CouponStockConflictException ex) {
            couponGrantTaskMapper.updateStatus(event.taskId(), CouponConstants.GRANT_TASK_FAILED, "库存不足，批次发放失败");
            log.error("Coupon grant rejected by database, taskId={}, batchId={}", event.taskId(), event.batchId(), ex);
        } finally {
            redisTemplate.delete(processingKey);
        }
    }
}
