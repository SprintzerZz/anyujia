package com.anyujia.anyujiachatservice.mq.consumer;

import com.anyujia.anyujiachatapi.domain.dto.ChatFrameDTO;
import com.anyujia.anyujiachatservice.mq.event.ChatMessagePushEvent;
import com.anyujia.anyujiachatservice.realtime.ChatConnectionRegistry;
import com.anyujia.anyujiachatservice.realtime.ChatDeliveryAckService;
import com.anyujia.anyujiachatservice.metrics.ChatMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMessagePushConsumer {
    private final ChatConnectionRegistry connectionRegistry;
    private final ChatDeliveryAckService deliveryAckService;
    private final ObjectMapper objectMapper;
    private final ChatMetrics chatMetrics;

    @Value("${chat.delivery.ack-timeout-millis:5000}")
    private long ackTimeoutMillis;

    @RabbitListener(queues = "#{chatPushQueue.name}")
    public void consume(ChatMessagePushEvent event) {
        try {
            chatMetrics.recordPushQueueDelay(event.getPublishedAtMillis());
            // Register before sending so a fast client ACK cannot overtake tracking.
            long trackStart = System.nanoTime();
            try {
                deliveryAckService.track(event.getRecipientId(), event.getMessage(),
                        event.getAttempts(), ackTimeoutMillis);
            } finally {
                chatMetrics.recordPushAckTrack(trackStart);
            }
            long sendStart = System.nanoTime();
            connectionRegistry.send(event.getRecipientId(),
                    objectMapper.writeValueAsString(new ChatFrameDTO<>(1,
                            event.getMessage().getMessageId(), "chat", event.getMessage())));
            chatMetrics.recordPushSocketSend(sendStart);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize chat push", ex);
        }
    }
}
