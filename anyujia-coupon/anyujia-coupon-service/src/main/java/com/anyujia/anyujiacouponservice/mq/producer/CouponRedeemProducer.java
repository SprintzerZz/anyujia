package com.anyujia.anyujiacouponservice.mq.producer;

import com.anyujia.anyujiacouponservice.mq.event.CouponRedeemEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
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
public class CouponRedeemProducer {

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${coupon.rocketmq.redeem-topic:coupon-redeem-topic}")
    private String redeemTopic;

    public void send(CouponRedeemEvent event) {
        Message<CouponRedeemEvent> message = MessageBuilder.withPayload(event)
                .setHeader(MessageConst.PROPERTY_KEYS, event.requestId())
                .build();
        SendResult result = rocketMQTemplate.syncSend(redeemTopic, message);
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("RocketMQ coupon redeem message send failed: " + result);
        }
    }

    public void sendAsync(CouponRedeemEvent event, Runnable failureCallback) {
        Message<CouponRedeemEvent> message = MessageBuilder.withPayload(event)
                .setHeader(MessageConst.PROPERTY_KEYS, event.requestId())
                .build();
        rocketMQTemplate.asyncSend(redeemTopic, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
                    log.warn("RocketMQ coupon redeem async send returned non-ok, requestId={}, result={}",
                            event.requestId(), sendResult);
                    failureCallback.run();
                }
            }

            @Override
            public void onException(Throwable throwable) {
                log.warn("RocketMQ coupon redeem async send failed, requestId={}", event.requestId(), throwable);
                failureCallback.run();
            }
        });
    }
}
