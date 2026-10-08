package com.anyujia.anyujiacommonrabbitmq.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 公共配置。
 *
 * <p>统一配置 RabbitMQ 消息序列化方式，发送消息时将 Java 对象转换为 JSON，
 * 消费消息时再从 JSON 转换为目标对象，避免默认 Java 序列化带来的可读性差、
 * 跨语言兼容性差等问题。</p>
 */
@Configuration
public class RabbitMqCommonConfig {

    /**
     * JSON 消息序列化转换器。
     *
     * <p>Spring AMQP 的 {@link Jackson2JsonMessageConverter} 基于 Jackson 实现，
     * 会被 {@code RabbitTemplate} 和监听容器用于消息体转换。</p>
     *
     * @return 消息序列化转换器
     */
    @Bean
    public MessageConverter jsonToMapMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
