package com.anyujia.anyujiachatservice.mq.consumer;

import com.anyujia.anyujiachatapi.domain.dto.ChatFrameDTO;
import com.anyujia.anyujiachatservice.mq.event.ChatMessageAckEvent;
import com.anyujia.anyujiachatservice.realtime.ChatConnectionRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMessageAckConsumer {
    private final ChatConnectionRegistry connectionRegistry;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "#{chatAckQueue.name}")
    public void consume(ChatMessageAckEvent event) {
        try {
            connectionRegistry.send(event.getSenderId(), objectMapper.writeValueAsString(
                    new ChatFrameDTO<>(1, event.getRequestId(), "chat_ack", event.getMessage())));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize chat acknowledgement", ex);
        }
    }
}
