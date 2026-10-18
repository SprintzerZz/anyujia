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
            channel.txSelect();
            int count = 0;
            try {
                while (count < limit) {
                    GetResponse response = channel.basicGet(PERSIST_DLQ, false);
                    if (response == null) {
                        break;
                    }
                    channel.basicPublish(
                            ChatRabbitConfig.PERSIST_EXCHANGE,
                            ChatRabbitConfig.PERSIST_QUEUE,
                            response.getProps(),
                            response.getBody());
                    channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
                    count++;
                }
                channel.txCommit();
                return count;
            } catch (Exception exception) {
                channel.txRollback();
                throw exception;
            }
        });
        return replayed == null ? 0 : replayed;
    }
}
