package com.anyujia.anyujiacouponservice.mq.producer;

import com.anyujia.anyujiacouponservice.mq.event.CouponGrantBatchEvent;
import lombok.RequiredArgsConstructor;
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
public class CouponGrantBatchProducer {

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${coupon.rocketmq.grant-topic:coupon-grant-topic}")
    private String grantTopic;

    public void send(CouponGrantBatchEvent event) {
        Message<CouponGrantBatchEvent> message = MessageBuilder.withPayload(event)
                .setHeader(MessageConst.PROPERTY_KEYS, event.batchId())
                .build();
        SendResult result = rocketMQTemplate.syncSend(grantTopic, message);
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("RocketMQ coupon grant message send failed: " + result);
        }
    }
}
