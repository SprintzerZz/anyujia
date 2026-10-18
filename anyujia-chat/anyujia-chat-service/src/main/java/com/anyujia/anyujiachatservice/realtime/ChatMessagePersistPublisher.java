package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiachatservice.mq.config.ChatRabbitConfig;
import com.anyujia.anyujiachatservice.mq.event.ChatMessagePersistEvent;
import lombok.RequiredArgsConstructor;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessagePersistPublisher {
    private final RabbitTemplate rabbitTemplate;

    @Value("${chat.rabbit.publisher-confirm-timeout-millis:3000}")
    private long confirmTimeoutMillis;

    public void publish(ChatMessagePersistEvent event) {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend(ChatRabbitConfig.PERSIST_EXCHANGE,
                ChatRabbitConfig.PERSIST_QUEUE, event, correlation);
        try {
            var confirm = correlation.getFuture().get(confirmTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ rejected chat persistence message: "
                        + confirm.getReason());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while confirming chat persistence message", ex);
        } catch (TimeoutException ex) {
            throw new IllegalStateException("Timed out waiting for chat persistence message confirmation", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to confirm chat persistence message", ex);
        }
    }
}
