package com.anyujia.anyujiachatservice.mq.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anyujia.anyujiachatapi.domain.dto.MessageSendReqDTO;
import com.anyujia.anyujiachatservice.metrics.ChatMetrics;
import com.anyujia.anyujiachatservice.mq.event.ChatMessagePersistEvent;
import com.anyujia.anyujiachatservice.realtime.ChatAckRouter;
import com.anyujia.anyujiachatservice.service.MessageService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ChatMessagePersistConsumerTest {
    @Test
    void consumesOneBatchWithinTheListenerBoundary() {
        MessageService messageService = mock(MessageService.class);
        ChatAckRouter ackRouter = mock(ChatAckRouter.class);
        ChatMetrics chatMetrics = mock(ChatMetrics.class);
        ChatMessagePersistConsumer consumer =
                new ChatMessagePersistConsumer(messageService, ackRouter, chatMetrics);
        ChatMessagePersistEvent first = new ChatMessagePersistEvent(10L, "r1", request(1L, "c1"));
        ChatMessagePersistEvent second = new ChatMessagePersistEvent(11L, "r2", request(2L, "c2"));

        consumer.consume(List.of(first, second));

        verify(messageService, times(2)).send(any(Long.class), any(MessageSendReqDTO.class));
        verify(ackRouter, times(2)).route(any());
        verify(chatMetrics).recordPersistLatency(any(Long.class));
    }

    @Test
    void emitsPersistedAcknowledgementOnlyAfterTransactionCommit() {
        MessageService messageService = mock(MessageService.class);
        ChatAckRouter ackRouter = mock(ChatAckRouter.class);
        ChatMetrics chatMetrics = mock(ChatMetrics.class);
        ChatMessagePersistConsumer consumer =
                new ChatMessagePersistConsumer(messageService, ackRouter, chatMetrics);
        ChatMessagePersistEvent event = new ChatMessagePersistEvent(10L, "r1", request(1L, "c1"));
        when(messageService.send(any(Long.class), any(MessageSendReqDTO.class)))
                .thenReturn(new com.anyujia.anyujiachatapi.domain.vo.MessageVO());

        TransactionSynchronizationManager.initSynchronization();
        try {
            consumer.consume(List.of(event));
            verify(ackRouter, never()).route(any());
            verify(chatMetrics, never()).recordPersistLatency(any(Long.class));
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(ackRouter).route(any());
            verify(chatMetrics).recordPersistLatency(any(Long.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static MessageSendReqDTO request(Long sessionId, String clientMessageId) {
        MessageSendReqDTO request = new MessageSendReqDTO();
        request.setSessionId(sessionId);
        request.setClientMessageId(clientMessageId);
        request.setType(0);
        request.setContent("hello");
        return request;
    }
}
