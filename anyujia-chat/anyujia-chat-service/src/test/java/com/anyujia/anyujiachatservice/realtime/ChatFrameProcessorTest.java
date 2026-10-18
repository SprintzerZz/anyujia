package com.anyujia.anyujiachatservice.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.anyujia.anyujiachatservice.mq.event.ChatMessagePersistEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ChatFrameProcessorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatMessagePersistPublisher persistPublisher = mock(ChatMessagePersistPublisher.class);
    private final ChatDeliveryAckService deliveryAckService =
            new ChatDeliveryAckService(null, objectMapper);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ChatFrameProcessor processor = new ChatFrameProcessor(
            persistPublisher, validator, objectMapper, deliveryAckService);

    @Test
    void rejectsInvalidJsonWithStableErrorCode() throws Exception {
        String response = process("{bad");
        JsonNode frame = objectMapper.readTree(response);

        assertThat(frame.get("type").asText()).isEqualTo("error");
        assertThat(frame.get("data").get("code").asText()).isEqualTo("CHAT-4001");
        verify(persistPublisher, never()).publish(org.mockito.ArgumentMatchers.any(ChatMessagePersistEvent.class));
    }

    @Test
    void validatesDecodedMessageBeforeCallingBlockingService() throws Exception {
        String response = process("""
                {"version":1,"requestId":"req-1","type":"chat","data":
                {"sessionId":null,"clientMessageId":"","type":0,"content":""}}
                """);
        JsonNode frame = objectMapper.readTree(response);

        assertThat(frame.get("data").get("code").asText()).isEqualTo("CHAT-4005");
        verify(persistPublisher, never()).publish(org.mockito.ArgumentMatchers.any(ChatMessagePersistEvent.class));
    }

    @Test
    void preservesRequestIdInHeartbeatResponse() throws Exception {
        JsonNode frame = objectMapper.readTree(process(
                "{\"version\":1,\"requestId\":\"req-7\",\"type\":\"heart_beat\",\"data\":null}"));

        assertThat(frame.get("version").asInt()).isEqualTo(1);
        assertThat(frame.get("requestId").asText()).isEqualTo("req-7");
        assertThat(frame.get("data").asText()).isEqualTo("pong");
    }

    @Test
    void acceptsValidMessageAfterPublishingDurableCommand() throws Exception {
        JsonNode frame = objectMapper.readTree(process("""
                {"version":1,"requestId":"req-8","type":"chat","data":
                {"sessionId":1,"clientMessageId":"client-1","type":0,"content":"hello"}}
                """));

        assertThat(frame.get("type").asText()).isEqualTo("chat_accepted");
        assertThat(frame.get("requestId").asText()).isEqualTo("req-8");
        verify(persistPublisher).publish(org.mockito.ArgumentMatchers.any(ChatMessagePersistEvent.class));
    }

    private String process(String payload) {
        String[] response = new String[1];
        processor.process(7L, payload, value -> response[0] = value);
        return response[0];
    }
}
