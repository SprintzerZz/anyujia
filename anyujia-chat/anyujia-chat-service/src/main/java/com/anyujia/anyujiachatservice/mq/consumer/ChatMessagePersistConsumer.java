package com.anyujia.anyujiachatservice.mq.consumer;

import com.anyujia.anyujiachatservice.mq.event.ChatMessagePersistEvent;
import com.anyujia.anyujiachatservice.mq.event.ChatMessageAckEvent;
import com.anyujia.anyujiachatservice.realtime.ChatAckRouter;
import com.anyujia.anyujiachatservice.service.MessageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChatMessagePersistConsumer {
    private final MessageService messageService;
    private final ChatAckRouter ackRouter;

    @RabbitListener(queues = "chat.message.persist.queue",
            containerFactory = "chatPersistBatchListenerContainerFactory")
    @Transactional(rollbackFor = Exception.class)
    public void consume(List<ChatMessagePersistEvent> events) {
        // One transaction covers the batch. Broker acknowledgement happens only after this method
        // returns successfully, so a crash before commit causes the batch to be redelivered.
        for (ChatMessagePersistEvent event : events) {
            // send() is idempotent by (senderId, clientMessageId), so broker redelivery is safe.
            var saved = messageService.send(event.getSenderId(), event.getRequest());
            ackRouter.route(new ChatMessageAckEvent(event.getSenderId(), event.getRequestId(), saved));
        }
    }
}
