package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiachatapi.domain.dto.ChatFrameDTO;
import com.anyujia.anyujiachatapi.domain.ChatProtocolErrorCode;
import com.anyujia.anyujiachatapi.domain.dto.ChatErrorDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageSendReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiachatservice.service.MessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Map;
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
    private final ChatDeliveryAckService deliveryAckService;

    public void process(Long userId, String payload, Consumer<String> response) {
        ChatFrameDTO<?> frame;
        try {
            frame = objectMapper.readValue(payload, ChatFrameDTO.class);
        } catch (JsonProcessingException ex) {
            respondError(response, null, ChatProtocolErrorCode.INVALID_JSON);
            return;
        }
        if (frame == null) {
            respondError(response, null, ChatProtocolErrorCode.INVALID_FRAME);
            return;
        }
        if (!validator.validate(frame).isEmpty()) {
            respondError(response, frame.getRequestId(), ChatProtocolErrorCode.INVALID_FRAME);
            return;
        }
        if (frame.getVersion() != 1) {
            respondError(response, frame.getRequestId(), ChatProtocolErrorCode.UNSUPPORTED_VERSION);
            return;
        }
        if ("heart_beat".equalsIgnoreCase(frame.getType())) {
            respond(response, new ChatFrameDTO<>(1, frame.getRequestId(), "heart_beat", "pong"));
            return;
        }
        if ("delivery_ack".equalsIgnoreCase(frame.getType())) {
            String messageId = extractMessageId(frame);
            if (messageId != null) {
                deliveryAckService.ack(userId, messageId);
            }
            return;
        }
        if (!"chat".equalsIgnoreCase(frame.getType())) {
            respondError(response, frame.getRequestId(), ChatProtocolErrorCode.UNSUPPORTED_TYPE);
            return;
        }
        MessageSendReqDTO request;
        try {
            request = objectMapper.convertValue(frame.getData(), MessageSendReqDTO.class);
        } catch (IllegalArgumentException ex) {
            respondError(response, frame.getRequestId(), ChatProtocolErrorCode.INVALID_MESSAGE);
            return;
        }
        Set<ConstraintViolation<MessageSendReqDTO>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            respondError(response, frame.getRequestId(), ChatProtocolErrorCode.INVALID_MESSAGE);
            return;
        }
        MessageVO saved = messageService.send(userId, request);
        respond(response, new ChatFrameDTO<>(1, frame.getRequestId(), "chat_ack", saved));
    }

    private String extractMessageId(ChatFrameDTO<?> frame) {
        if (frame.getData() instanceof Map<?, ?> data) {
            Object messageId = data.get("messageId");
            return messageId == null ? null : String.valueOf(messageId);
        }
        return null;
    }

    private void respond(Consumer<String> response, ChatFrameDTO<?> frame) {
        try {
            response.accept(objectMapper.writeValueAsString(frame));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize chat frame", ex);
        }
    }

    private void respondError(Consumer<String> response, String requestId, ChatProtocolErrorCode error) {
        respond(response, new ChatFrameDTO<>(1, requestId, "error",
                new ChatErrorDTO(error.getCode(), error.getMessage())));
    }
}
