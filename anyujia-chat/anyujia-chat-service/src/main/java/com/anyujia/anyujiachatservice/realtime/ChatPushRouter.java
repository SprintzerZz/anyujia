package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiachatservice.mq.config.ChatRabbitConfig;
import com.anyujia.anyujiachatservice.mq.event.ChatMessagePushEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatPushRouter implements ChatMessagePublisher {
    private final AmqpTemplate amqpTemplate;
    private final ChatRecipientNodeLocator nodeLocator;

    @Override
    public void route(Long recipientId, MessageVO message) {
        ChatMessagePushEvent event = new ChatMessagePushEvent(recipientId, message);
        for (String nodeId : nodeLocator.nodes(recipientId)) {
            amqpTemplate.convertAndSend(ChatRabbitConfig.PUSH_EXCHANGE, nodeId, event);
        }
    }
}
