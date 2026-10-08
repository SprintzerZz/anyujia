package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiachatapi.domain.dto.ChatFrameDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageSendReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiachatservice.service.MessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatFrameProcessor {
    private final MessageService messageService;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public void process(Long userId, String payload, Consumer<String> response) {
        ChatFrameDTO<?> frame;
        try {
            frame = objectMapper.readValue(payload, ChatFrameDTO.class);
        } catch (JsonProcessingException ex) {
            respond(response, new ChatFrameDTO<>("error", "Invalid chat frame"));
            return;
        }
        if (frame == null || frame.getType() == null) {
            respond(response, new ChatFrameDTO<>("error", "Invalid chat frame"));
            return;
        }
        if ("heart_beat".equalsIgnoreCase(frame.getType())) {
            respond(response, new ChatFrameDTO<>("heart_beat", "pong"));
            return;
        }
        if (!"chat".equalsIgnoreCase(frame.getType())) {
            respond(response, new ChatFrameDTO<>("error", "Unsupported chat frame type"));
            return;
        }
        MessageSendReqDTO request;
        try {
            request = objectMapper.convertValue(frame.getData(), MessageSendReqDTO.class);
        } catch (IllegalArgumentException ex) {
            respond(response, new ChatFrameDTO<>("error", "Invalid chat message"));
            return;
        }
        Set<ConstraintViolation<MessageSendReqDTO>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            respond(response, new ChatFrameDTO<>("error", violations.iterator().next().getMessage()));
            return;
        }
        MessageVO saved = messageService.send(userId, request);
        respond(response, new ChatFrameDTO<>("chat_ack", saved));
    }

    private void respond(Consumer<String> response, ChatFrameDTO<?> frame) {
        try {
            response.accept(objectMapper.writeValueAsString(frame));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize chat frame", ex);
        }
    }
}
