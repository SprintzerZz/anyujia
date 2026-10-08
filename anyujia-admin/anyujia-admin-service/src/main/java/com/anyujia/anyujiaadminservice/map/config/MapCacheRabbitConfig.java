package com.anyujia.anyujiaadminservice.map.config;

import java.util.Map;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 地图缓存失效广播 RabbitMQ 配置。
 */
@Configuration
@ConditionalOnProperty(prefix = "map.cache", name = "invalidate-broadcast-enabled", havingValue = "true")
public class MapCacheRabbitConfig {

    /**
     * 广播交换机，所有实例都会收到缓存失效通知。
     *
     * @param mapCacheProperties 地图缓存配置
     * @return Fanout 交换机
     */
    @Bean
    public FanoutExchange mapCacheInvalidateExchange(MapCacheProperties mapCacheProperties) {
        return new FanoutExchange(mapCacheProperties.getInvalidateExchange(), true, false);
    }

    /**
     * 当前实例专属匿名队列，实例下线后自动删除。
     *
     * @param mapCacheProperties 地图缓存配置
     * @return 匿名队列
     */
    @Bean
    public Queue mapCacheInvalidateQueue(MapCacheProperties mapCacheProperties) {
        return new AnonymousQueue(Map.of(
                "x-dead-letter-exchange",
                mapCacheProperties.getInvalidateDeadLetterExchange()));
    }

    /**
     * 将当前实例队列绑定到广播交换机。
     *
     * @param mapCacheInvalidateQueue 当前实例队列
     * @param mapCacheInvalidateExchange 广播交换机
     * @return 绑定关系
     */
    @Bean
    public Binding mapCacheInvalidateBinding(
            Queue mapCacheInvalidateQueue,
            FanoutExchange mapCacheInvalidateExchange) {
        return BindingBuilder.bind(mapCacheInvalidateQueue).to(mapCacheInvalidateExchange);
    }

    /**
     * 缓存失效死信交换机。
     *
     * @param mapCacheProperties 地图缓存配置
     * @return 死信交换机
     */
    @Bean
    public FanoutExchange mapCacheInvalidateDeadLetterExchange(MapCacheProperties mapCacheProperties) {
        return new FanoutExchange(mapCacheProperties.getInvalidateDeadLetterExchange(), true, false);
    }

    /**
     * 缓存失效死信队列。
     *
     * @param mapCacheProperties 地图缓存配置
     * @return 死信队列
     */
    @Bean
    public Queue mapCacheInvalidateDeadLetterQueue(MapCacheProperties mapCacheProperties) {
        return new Queue(mapCacheProperties.getInvalidateDeadLetterQueue(), true);
    }

    /**
     * 绑定死信队列。
     *
     * @param mapCacheInvalidateDeadLetterQueue 死信队列
     * @param mapCacheInvalidateDeadLetterExchange 死信交换机
     * @return 绑定关系
     */
    @Bean
    public Binding mapCacheInvalidateDeadLetterBinding(
            Queue mapCacheInvalidateDeadLetterQueue,
            FanoutExchange mapCacheInvalidateDeadLetterExchange) {
        return BindingBuilder.bind(mapCacheInvalidateDeadLetterQueue).to(mapCacheInvalidateDeadLetterExchange);
    }
}
