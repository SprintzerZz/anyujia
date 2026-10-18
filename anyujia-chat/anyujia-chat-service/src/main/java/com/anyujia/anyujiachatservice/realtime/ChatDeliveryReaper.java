package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiachatservice.mq.config.ChatRabbitConfig;
import com.anyujia.anyujiachatservice.mq.event.ChatMessagePushEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Re-pushes messages whose delivery was not acknowledged and dead-letters those that keep
 * failing. Runs periodically and relies on {@link ChatDeliveryAckService} for pending state.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatDeliveryReaper {
    private final ChatDeliveryAckService ackService;
    private final ChatPushRouter pushRouter;
    private final AmqpTemplate amqpTemplate;

    @Value("${chat.delivery.ack-timeout-millis:5000}")
    private long ackTimeoutMillis;

    @Value("${chat.delivery.max-retries:3}")
    private int maxRetries;

    @Scheduled(fixedDelayString = "${chat.delivery.reap-millis:2000}")
    public void reap() {
        List<ChatDeliveryAckService.PendingDelivery> overdue =
                ackService.overdue(System.currentTimeMillis(), 100);
        for (ChatDeliveryAckService.PendingDelivery pending : overdue) {
            Long recipientId = pending.getRecipientId();
            String messageId = pending.getMessage().getMessageId();
            ackService.remove(recipientId, messageId);
            if (pending.getAttempts() < maxRetries) {
                pushRouter.route(recipientId, pending.getMessage(), pending.getAttempts() + 1);
            } else {
                amqpTemplate.convertAndSend(ChatRabbitConfig.PUSH_DLX, ChatRabbitConfig.DLQ_ROUTING_KEY,
                        new ChatMessagePushEvent(recipientId, pending.getMessage(), pending.getAttempts()));
                log.warn("Chat message dead-lettered after {} attempts, messageId={}, recipientId={}",
                        pending.getAttempts(), messageId, recipientId);
            }
        }
    }
}
