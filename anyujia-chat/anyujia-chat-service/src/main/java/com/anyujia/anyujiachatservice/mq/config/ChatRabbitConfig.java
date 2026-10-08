package com.anyujia.anyujiachatservice.mq.config;

import com.anyujia.anyujiachatservice.realtime.ChatNodeIdentity;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatRabbitConfig {
    public static final String PUSH_EXCHANGE = "chat.message.push.exchange";

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
    public Binding chatPushBinding(Queue chatPushQueue, DirectExchange chatPushExchange,
                                   ChatNodeIdentity nodeIdentity) {
        return BindingBuilder.bind(chatPushQueue).to(chatPushExchange).with(nodeIdentity.getNodeId());
    }
}
