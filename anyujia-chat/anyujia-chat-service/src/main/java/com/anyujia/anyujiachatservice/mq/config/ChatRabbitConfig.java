package com.anyujia.anyujiachatservice.mq.config;

import com.anyujia.anyujiachatservice.realtime.ChatNodeIdentity;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatRabbitConfig {
    public static final String PUSH_EXCHANGE = "chat.message.push.exchange";
    public static final String PUSH_DLX = "chat.message.push.dlx";
    public static final String PERSIST_EXCHANGE = "chat.message.persist.exchange";
    public static final String PERSIST_QUEUE = "chat.message.persist.queue";
    public static final String PERSIST_DLX = "chat.message.persist.dlx";
    public static final String ACK_EXCHANGE = "chat.message.ack.exchange";
    public static final String DLQ_ROUTING_KEY = "dlq";

    @Bean
    public DirectExchange chatPushExchange() {
        return new DirectExchange(PUSH_EXCHANGE, true, false);
    }

    @Bean
    public Queue chatPushQueue(ChatNodeIdentity nodeIdentity) {
        return QueueBuilder.nonDurable("chat.message.push." + nodeIdentity.getNodeId())
                .exclusive()
                .autoDelete()
                .build();
    }

    @Bean
    public Binding chatPushBinding(@Qualifier("chatPushQueue") Queue chatPushQueue,
                                   @Qualifier("chatPushExchange") DirectExchange chatPushExchange,
                                   ChatNodeIdentity nodeIdentity) {
        return BindingBuilder.bind(chatPushQueue).to(chatPushExchange).with(nodeIdentity.getNodeId());
    }

    @Bean
    public DirectExchange chatPushDlx() {
        return new DirectExchange(PUSH_DLX, true, false);
    }

    @Bean
    public Queue chatPushDlq() {
        return QueueBuilder.durable("chat.message.push.dlq").build();
    }

    @Bean
    public Binding chatPushDlqBinding(@Qualifier("chatPushDlq") Queue chatPushDlq,
                                      @Qualifier("chatPushDlx") DirectExchange chatPushDlx) {
        return BindingBuilder.bind(chatPushDlq).to(chatPushDlx).with(DLQ_ROUTING_KEY);
    }

    @Bean
    public DirectExchange chatPersistExchange() {
        return new DirectExchange(PERSIST_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange chatPersistDlx() {
        return new DirectExchange(PERSIST_DLX, true, false);
    }

    @Bean
    public Queue chatPersistQueue() {
        return QueueBuilder.durable(PERSIST_QUEUE)
                .deadLetterExchange(PERSIST_DLX)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding chatPersistBinding(@Qualifier("chatPersistQueue") Queue queue,
                                      @Qualifier("chatPersistExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(PERSIST_QUEUE);
    }

    @Bean
    public Queue chatPersistDlq() {
        return QueueBuilder.durable("chat.message.persist.dlq").build();
    }

    @Bean
    public Binding chatPersistDlqBinding(@Qualifier("chatPersistDlq") Queue queue,
                                         @Qualifier("chatPersistDlx") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(DLQ_ROUTING_KEY);
    }

    @Bean
    public DirectExchange chatAckExchange() {
        return new DirectExchange(ACK_EXCHANGE, true, false);
    }

    @Bean
    public Queue chatAckQueue(ChatNodeIdentity nodeIdentity) {
        return QueueBuilder.nonDurable("chat.message.ack." + nodeIdentity.getNodeId())
                .exclusive()
                .autoDelete()
                .build();
    }

    @Bean
    public Binding chatAckBinding(@Qualifier("chatAckQueue") Queue queue,
                                  @Qualifier("chatAckExchange") DirectExchange exchange,
                                  ChatNodeIdentity nodeIdentity) {
        return BindingBuilder.bind(queue).to(exchange).with(nodeIdentity.getNodeId());
    }
}
