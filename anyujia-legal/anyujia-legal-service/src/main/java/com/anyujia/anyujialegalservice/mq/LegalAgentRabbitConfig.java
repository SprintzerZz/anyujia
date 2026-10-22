package com.anyujia.anyujialegalservice.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class LegalAgentRabbitConfig {
    public static final String EXCHANGE = "legal.agent.task.exchange";
    public static final String ROUTING_KEY = "legal.agent.task";
    public static final String QUEUE = "legal.agent.task.queue";
    public static final String DLX = "legal.agent.task.dlx";
    public static final String DLQ = "legal.agent.task.dlq";

    @Bean
    public DirectExchange legalAgentTaskExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange legalAgentTaskDlx() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue legalAgentTaskQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ)
                .build();
    }

    @Bean
    public Binding legalAgentTaskBinding(@Qualifier("legalAgentTaskQueue") Queue legalAgentTaskQueue,
                                        @Qualifier("legalAgentTaskExchange") DirectExchange legalAgentTaskExchange) {
        return BindingBuilder.bind(legalAgentTaskQueue).to(legalAgentTaskExchange).with(ROUTING_KEY);
    }

    @Bean
    public Queue legalAgentTaskDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding legalAgentTaskDlqBinding(@Qualifier("legalAgentTaskDlq") Queue legalAgentTaskDlq,
                                           @Qualifier("legalAgentTaskDlx") DirectExchange legalAgentTaskDlx) {
        return BindingBuilder.bind(legalAgentTaskDlq).to(legalAgentTaskDlx).with(DLQ);
    }
}
