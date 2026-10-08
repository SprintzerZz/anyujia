package com.anyujia.anyujiacommonrabbitmq.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 广播消息服务。
 */
@Component
@ConditionalOnBean(RabbitTemplate.class)
public class RabbitBroadcastService {

    /**
     * RabbitMQ 操作模板。
     */
    private final RabbitTemplate rabbitTemplate;

    public RabbitBroadcastService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 向 Fanout 交换机广播消息。
     *
     * @param exchange 交换机名称
     * @param message 消息内容
     */
    public void publishFanout(String exchange, Object message) {
        rabbitTemplate.convertAndSend(exchange, "", message);
    }
}
