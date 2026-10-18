package com.anyujia.anyujiachatservice.metrics;

import java.util.Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatPersistenceMetricsReporter {
    private static final String PERSIST_QUEUE = "chat.message.persist.queue";
    private static final String PERSIST_DLQ = "chat.message.persist.dlq";

    private final JdbcTemplate jdbcTemplate;
    private final AmqpAdmin amqpAdmin;
    private final ChatMetrics chatMetrics;

    @Scheduled(fixedDelayString = "${chat.metrics.refresh-millis:10000}")
    public void refresh() {
        refreshOutboxBacklog();
        refreshQueueDepths();
    }

    private void refreshOutboxBacklog() {
        try {
            Integer pending = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM chat_message_outbox WHERE status = 0", Integer.class);
            Integer processing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM chat_message_outbox WHERE status = 1", Integer.class);
            chatMetrics.setOutboxBacklog(pending == null ? 0 : pending,
                    processing == null ? 0 : processing);
        } catch (RuntimeException ex) {
            log.warn("Failed to refresh chat outbox backlog metric", ex);
        }
    }

    private void refreshQueueDepths() {
        try {
            chatMetrics.setPersistQueueDepths(queueDepth(PERSIST_QUEUE), queueDepth(PERSIST_DLQ));
        } catch (RuntimeException ex) {
            log.warn("Failed to refresh chat persistence queue depth metrics", ex);
        }
    }

    private int queueDepth(String queueName) {
        Properties properties = amqpAdmin.getQueueProperties(queueName);
        if (properties == null) {
            return 0;
        }
        Object count = properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        return count instanceof Number number ? number.intValue() : 0;
    }
}
