package com.anyujia.anyujiachatservice.mq.config;

import com.anyujia.anyujiachatservice.realtime.ChatNodeIdentity;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.retry.MessageBatchRecoverer;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

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

    @Bean("chatPersistBatchListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory chatPersistBatchListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Qualifier("chatPersistRetryInterceptor") RetryOperationsInterceptor retryInterceptor,
            @Value("${chat.rabbit.persist.batch-size:50}") int configuredBatchSize,
            @Value("${chat.rabbit.persist.receive-timeout-millis:100}") long receiveTimeoutMillis) {
        int batchSize = Math.max(1, Math.min(configuredBatchSize, 200));
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setBatchListener(true);
        factory.setConsumerBatchEnabled(true);
        factory.setBatchSize(batchSize);
        factory.setReceiveTimeout(Math.max(1L, receiveTimeoutMillis));
        factory.setPrefetchCount(Math.min(batchSize * 2, 400));
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryInterceptor);
        return factory;
    }

    @Bean("chatPersistRetryInterceptor")
    public RetryOperationsInterceptor chatPersistRetryInterceptor(
            @Value("${chat.rabbit.persist.retry.max-attempts:4}") int configuredMaxAttempts,
            @Value("${chat.rabbit.persist.retry.initial-interval-millis:500}") long configuredInitialInterval,
            @Value("${chat.rabbit.persist.retry.multiplier:2.0}") double configuredMultiplier,
            @Value("${chat.rabbit.persist.retry.max-interval-millis:8000}") long configuredMaxInterval) {
        int maxAttempts = Math.max(1, Math.min(configuredMaxAttempts, 10));
        long initialInterval = Math.max(1L, configuredInitialInterval);
        double multiplier = Math.max(1.0d, configuredMultiplier);
        long maxInterval = Math.max(initialInterval, configuredMaxInterval);
        MessageBatchRecoverer rejectBatch = (messages, cause) -> {
            throw new AmqpRejectAndDontRequeueException(
                    "Chat persistence retries exhausted for batch of " + messages.size(), cause);
        };
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(maxAttempts)
                .backOffOptions(initialInterval, multiplier, maxInterval)
                .recoverer(rejectBatch)
                .build();
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
