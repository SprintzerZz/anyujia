package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiachatservice.mq.config.ChatRabbitConfig;
import com.anyujia.anyujiachatservice.mq.event.ChatMessageAckEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatAckRouter {
    private final AmqpTemplate amqpTemplate;
    private final ChatRecipientNodeLocator nodeLocator;

    public void route(ChatMessageAckEvent event) {
        for (String nodeId : nodeLocator.nodes(event.getSenderId())) {
            amqpTemplate.convertAndSend(ChatRabbitConfig.ACK_EXCHANGE, nodeId, event);
        }
    }
}
