package com.anyujia.anyujiachatservice.mq.service;

import com.anyujia.anyujiachatservice.mq.config.ChatRabbitConfig;
import com.rabbitmq.client.GetResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatPersistDlqReplayService {
    private static final String PERSIST_DLQ = "chat.message.persist.dlq";

    private final RabbitOperations rabbitOperations;

    public int replay(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 1000));
        Integer replayed = rabbitOperations.execute(channel -> {
            int count = 0;
            while (count < limit) {
                GetResponse response = channel.basicGet(PERSIST_DLQ, false);
                if (response == null) {
                    break;
                }
                // Publish before ack: a crash in between redelivers the DLQ message and
                // produces a duplicate, which the idempotent (senderId, clientMessageId)
                // consumer deduplicates. There is no message-loss window.
                channel.basicPublish(
                        ChatRabbitConfig.PERSIST_EXCHANGE,
                        ChatRabbitConfig.PERSIST_QUEUE,
                        response.getProps(),
                        response.getBody());
                channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
                count++;
            }
            return count;
        });
        return replayed == null ? 0 : replayed;
    }
}
