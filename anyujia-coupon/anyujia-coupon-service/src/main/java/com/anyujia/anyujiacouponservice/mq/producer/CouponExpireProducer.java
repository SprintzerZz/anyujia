package com.anyujia.anyujiacouponservice.mq.producer;

import java.time.ZoneId;

import com.anyujia.anyujiacouponservice.domain.entity.CouponTemplate;
import com.anyujia.anyujiacouponservice.domain.entity.UserCoupon;
import com.anyujia.anyujiacouponservice.mq.event.CouponExpireEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CouponExpireProducer {

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${coupon.rocketmq.expire-topic:coupon-expire-topic}")
    private String expireTopic;

    @Value("${coupon.time-zone:Asia/Shanghai}")
    private String timeZone;

    public void sendExpireMessage(UserCoupon coupon, CouponTemplate template) {
        if (coupon == null || coupon.getId() == null || template == null || template.getValidEndTime() == null) {
            return;
        }
        long deliverAt = template.getValidEndTime()
                .atZone(ZoneId.of(timeZone))
                .toInstant()
                .toEpochMilli();
        if (deliverAt <= System.currentTimeMillis()) {
            return;
        }
        CouponExpireEvent event = new CouponExpireEvent(
                coupon.getId(), coupon.getUserId(), coupon.getUserType(), coupon.getTemplateId(), deliverAt);
        Message<CouponExpireEvent> message = MessageBuilder.withPayload(event)
                .setHeader(MessageConst.PROPERTY_KEYS, "coupon-expire-" + coupon.getId())
                .build();
        SendResult result = rocketMQTemplate.syncSendDeliverTimeMills(expireTopic, message, deliverAt);
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("RocketMQ coupon expire message send failed: " + result);
        }
    }
}
