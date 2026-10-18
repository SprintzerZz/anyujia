package com.anyujia.anyujiachatservice.mq.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
class ChatRabbitConfigTest {

    @Test
    void persistRetryIsBoundedAndRejectsBatchToDlqAfterExhaustion() throws Throwable {
        ChatRabbitConfig config = new ChatRabbitConfig();
        RetryOperationsInterceptor interceptor = config.chatPersistRetryInterceptor(3, 1, 2.0, 2);
        AtomicInteger attempts = new AtomicInteger();
        List<Message> batch = List.of(new Message("payload".getBytes(), new MessageProperties()));
        PersistListener target = (channel, messages) -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("mysql unavailable");
        };
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvice(interceptor);
        PersistListener listener = (PersistListener) proxyFactory.getProxy();

        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> listener.listen(new Object(), batch));
        assertEquals(3, attempts.get());
    }

    interface PersistListener {
        void listen(Object channel, List<Message> messages);
    }
}
