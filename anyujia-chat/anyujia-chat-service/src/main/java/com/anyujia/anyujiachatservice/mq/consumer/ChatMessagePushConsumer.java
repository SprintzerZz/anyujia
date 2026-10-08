package com.anyujia.anyujiachatservice.mq.consumer;

import com.anyujia.anyujiachatapi.domain.dto.ChatFrameDTO;
import com.anyujia.anyujiachatservice.mq.event.ChatMessagePushEvent;
import com.anyujia.anyujiachatservice.realtime.ChatConnectionRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMessagePushConsumer {
    private final ChatConnectionRegistry connectionRegistry;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "#{chatPushQueue.name}")
    public void consume(ChatMessagePushEvent event) {
        try {
            connectionRegistry.send(event.getRecipientId(),
                    objectMapper.writeValueAsString(new ChatFrameDTO<>("chat", event.getMessage())));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize chat push", ex);
        }
    }
}
