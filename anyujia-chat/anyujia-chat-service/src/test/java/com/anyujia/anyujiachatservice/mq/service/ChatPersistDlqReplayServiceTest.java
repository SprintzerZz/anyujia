package com.anyujia.anyujiachatservice.mq.service;

import com.anyujia.anyujiachatservice.mq.config.ChatRabbitConfig;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitOperations;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChatPersistDlqReplayServiceTest {

    @Test
    void republishesBeforeAcknowledgingWithoutRabbitTransaction() throws Exception {
        GetResponse response = new GetResponse(
                new Envelope(42L, false, "", ""),
                new AMQP.BasicProperties(),
                "payload".getBytes(),
                0);
        List<String> calls = new ArrayList<>();
        AtomicInteger gets = new AtomicInteger();
        Channel channel = (Channel) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Channel.class}, (proxy, method, arguments) -> {
                    calls.add(method.getName());
                    if (method.getName().equals("basicGet")) {
                        return gets.getAndIncrement() == 0 ? response : null;
                    }
                    if (method.getName().equals("basicPublish")) {
                        assertEquals(ChatRabbitConfig.PERSIST_EXCHANGE, arguments[0]);
                        assertEquals(ChatRabbitConfig.PERSIST_QUEUE, arguments[1]);
                        assertSame(response.getProps(), arguments[2]);
                        assertSame(response.getBody(), arguments[3]);
                    }
                    if (method.getName().equals("basicAck")) {
                        assertEquals(42L, arguments[0]);
                        assertEquals(false, arguments[1]);
                    }
                    return primitiveDefault(method.getReturnType());
                });
        RabbitOperations rabbitOperations = (RabbitOperations) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{RabbitOperations.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("execute")) {
                        return ((ChannelCallback<?>) arguments[0]).doInRabbit(channel);
                    }
                    return primitiveDefault(method.getReturnType());
                });

        int replayed = new ChatPersistDlqReplayService(rabbitOperations).replay(10);

        assertEquals(1, replayed);
        assertEquals(List.of("basicGet", "basicPublish", "basicAck", "basicGet"), calls);
    }

    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
