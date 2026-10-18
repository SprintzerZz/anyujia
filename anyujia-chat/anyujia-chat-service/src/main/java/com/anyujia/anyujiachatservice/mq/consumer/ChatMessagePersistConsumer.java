package com.anyujia.anyujiachatservice.mq.consumer;

import com.anyujia.anyujiachatservice.mq.event.ChatMessagePersistEvent;
import com.anyujia.anyujiachatservice.mq.event.ChatMessageAckEvent;
import com.anyujia.anyujiachatservice.realtime.ChatAckRouter;
import com.anyujia.anyujiachatservice.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMessagePersistConsumer {
    private final MessageService messageService;
    private final ChatAckRouter ackRouter;

    @RabbitListener(queues = "chat.message.persist.queue")
    public void consume(ChatMessagePersistEvent event) {
        // send() is idempotent by (senderId, clientMessageId), so broker redelivery is safe.
        var saved = messageService.send(event.getSenderId(), event.getRequest());
        ackRouter.route(new ChatMessageAckEvent(event.getSenderId(), event.getRequestId(), saved));
    }
}
