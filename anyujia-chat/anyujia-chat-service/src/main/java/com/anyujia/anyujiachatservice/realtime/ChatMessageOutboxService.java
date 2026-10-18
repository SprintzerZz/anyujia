package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMessageOutboxService {
    private static final String EVENT_TYPE = "recipient_push";
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ChatMessagePublisher pushRouter;

    @Value("${chat.outbox.batch-size:100}")
    private int batchSize;

    @Value("${chat.outbox.retry-delay-millis:1000}")
    private long retryDelayMillis;

    @Value("${chat.outbox.processing-timeout-millis:60000}")
    private long processingTimeoutMillis;

    @Transactional(rollbackFor = Exception.class)
    public void enqueue(Long recipientId, MessageVO message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            jdbcTemplate.update("""
                    INSERT IGNORE INTO chat_message_outbox
                        (id, message_id, recipient_id, event_type, payload,
                         status, retry_count, next_retry_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                    """, IdWorker.getId(), Long.valueOf(message.getMessageId()), recipientId,
                    EVENT_TYPE, payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize chat outbox payload", ex);
        }
    }

    @Scheduled(fixedDelayString = "${chat.outbox.poll-millis:1000}")
    public void publishPending() {
        int limit = Math.max(1, Math.min(batchSize, 500));
        Timestamp processingDeadline = Timestamp.from(Instant.now().minusMillis(
                Math.max(1_000L, processingTimeoutMillis)));
        List<OutboxRow> rows = jdbcTemplate.query("""
                SELECT id, message_id, recipient_id, payload, retry_count
                  FROM chat_message_outbox
                 WHERE (status = 0 AND next_retry_at <= CURRENT_TIMESTAMP(3))
                    OR (status = 1 AND updated_at <= ?)
                 ORDER BY id
                 LIMIT ?
                """, (rs, rowNum) -> new OutboxRow(
                rs.getLong("id"), rs.getLong("message_id"), rs.getLong("recipient_id"),
                rs.getString("payload"), rs.getInt("retry_count")), processingDeadline, limit);
        for (OutboxRow row : rows) {
            if (jdbcTemplate.update("UPDATE chat_message_outbox SET status = 1, updated_at = CURRENT_TIMESTAMP(3) "
                    + "WHERE id = ? AND (status = 0 OR (status = 1 AND updated_at <= ?))",
                    row.id(), processingDeadline) != 1) {
                continue;
            }
            try {
                MessageVO message = objectMapper.readValue(row.payload(), MessageVO.class);
                pushRouter.route(row.recipientId(), message);
                jdbcTemplate.update("UPDATE chat_message_outbox SET status = 2, updated_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE id = ?", row.id());
            } catch (Exception ex) {
                long delay = Math.min(retryDelayMillis * (1L << Math.min(row.retryCount(), 10)), 300_000L);
                jdbcTemplate.update("""
                        UPDATE chat_message_outbox
                           SET status = 0, retry_count = retry_count + 1,
                               next_retry_at = ?, updated_at = CURRENT_TIMESTAMP(3)
                         WHERE id = ?
                        """, Timestamp.from(Instant.now().plusMillis(delay)), row.id());
                log.warn("Chat outbox publish failed, outboxId={}, retryCount={}", row.id(),
                        row.retryCount() + 1, ex);
            }
        }
    }

    private record OutboxRow(long id, long messageId, long recipientId, String payload, int retryCount) {
    }
}
