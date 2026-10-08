package com.anyujia.anyujiachatservice.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiachatservice.mq.config.ChatRabbitConfig;
import com.anyujia.anyujiachatservice.mq.event.ChatMessagePushEvent;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AmqpTemplate;

class ChatPushRouterTest {
    @Test
    void routesOnlyToNodesHoldingRecipientConnections() {
        AmqpTemplate amqpTemplate = mock(AmqpTemplate.class);
        ChatRecipientNodeLocator nodeLocator = mock(ChatRecipientNodeLocator.class);
        ChatPushRouter router = new ChatPushRouter(amqpTemplate, nodeLocator);
        MessageVO message = new MessageVO();
        message.setMessageId("100");
        when(nodeLocator.nodes(2L)).thenReturn(Set.of("chat-a", "chat-c"));

        router.route(2L, message);

        ArgumentCaptor<ChatMessagePushEvent> event = ArgumentCaptor.forClass(ChatMessagePushEvent.class);
        verify(amqpTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(ChatRabbitConfig.PUSH_EXCHANGE),
                org.mockito.ArgumentMatchers.eq("chat-a"), event.capture());
        verify(amqpTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(ChatRabbitConfig.PUSH_EXCHANGE),
                org.mockito.ArgumentMatchers.eq("chat-c"), org.mockito.ArgumentMatchers.any(ChatMessagePushEvent.class));
        org.assertj.core.api.Assertions.assertThat(event.getValue().getRecipientId()).isEqualTo(2L);
    }
}
