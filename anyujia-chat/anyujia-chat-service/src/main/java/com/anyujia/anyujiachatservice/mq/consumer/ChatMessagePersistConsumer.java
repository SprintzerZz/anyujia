package com.anyujia.anyujiachatservice.mq.consumer;

import com.anyujia.anyujiachatservice.mq.event.ChatMessagePersistEvent;
import com.anyujia.anyujiachatservice.mq.event.ChatMessageAckEvent;
import com.anyujia.anyujiachatservice.metrics.ChatMetrics;
import com.anyujia.anyujiachatservice.realtime.ChatAckRouter;
import com.anyujia.anyujiachatservice.service.MessageService;
import java.util.List;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class ChatMessagePersistConsumer {
    private final MessageService messageService;
    private final ChatAckRouter ackRouter;
    private final ChatMetrics chatMetrics;

    @RabbitListener(queues = "chat.message.persist.queue",
            containerFactory = "chatPersistBatchListenerContainerFactory")
    @Transactional(rollbackFor = Exception.class)
    public void consume(List<ChatMessagePersistEvent> events) {
        // One transaction covers the batch. Broker acknowledgement happens only after this method
        // returns successfully, so a crash before commit causes the batch to be redelivered.
        long startNanos = System.nanoTime();
        List<ChatMessageAckEvent> acknowledgements = new ArrayList<>(events.size());
        for (ChatMessagePersistEvent event : events) {
            // send() is idempotent by (senderId, clientMessageId), so broker redelivery is safe.
            var saved = messageService.send(event.getSenderId(), event.getRequest());
            acknowledgements.add(new ChatMessageAckEvent(event.getSenderId(), event.getRequestId(), saved));
        }
        routeAfterCommit(acknowledgements, startNanos);
    }

    private void routeAfterCommit(List<ChatMessageAckEvent> acknowledgements, long startNanos) {
        Runnable route = () -> {
            chatMetrics.recordPersistLatency(startNanos);
            acknowledgements.forEach(ackRouter::route);
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            route.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                route.run();
            }
        });
    }
}
