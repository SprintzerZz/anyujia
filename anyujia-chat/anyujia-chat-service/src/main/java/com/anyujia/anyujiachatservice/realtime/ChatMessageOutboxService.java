package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiachatservice.cache.RecentMessageCache;
import com.anyujia.anyujiachatservice.cache.ChatConversationIndexCache;
import com.anyujia.anyujiachatservice.service.unread.ChatUnreadService;
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
    public static final String EVENT_RECIPIENT_PUSH = "recipient_push";
    public static final String EVENT_UNREAD_REFRESH = "unread_refresh";
    public static final String EVENT_CONVERSATION_INDEX = "conversation_index";
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ChatMessagePublisher pushRouter;
    private final RecentMessageCache recentMessageCache;
    private final ChatUnreadService unreadService;
    private final ChatConversationIndexCache conversationIndexCache;

    @Value("${chat.outbox.batch-size:100}")
    private int batchSize;

    @Value("${chat.outbox.retry-delay-millis:1000}")
    private long retryDelayMillis;

    @Value("${chat.outbox.processing-timeout-millis:60000}")
    private long processingTimeoutMillis;

    @Value("${chat.outbox.sent-retention-days:7}")
    private int sentRetentionDays;

    @Value("${chat.outbox.cleanup-batch-size:1000}")
    private int cleanupBatchSize;

    @Transactional(rollbackFor = Exception.class)
    public void enqueue(Long recipientId, MessageVO message) {
        enqueueEvent(recipientId, EVENT_RECIPIENT_PUSH, message);
    }

    public void enqueueMessageEvents(Long recipientId, List<Long> affectedUsers,
                                     boolean incrementUnread, MessageVO message) {
        enqueue(recipientId, message);
        enqueueDerivedEvents(affectedUsers, incrementUnread, recipientId, message);
    }

    public void enqueueDerivedEvents(List<Long> affectedUsers, boolean incrementUnread,
                                     Long unreadUserId, MessageVO message) {
        if (incrementUnread && unreadUserId != null) {
            enqueueEvent(unreadUserId, EVENT_UNREAD_REFRESH, message);
        }
        for (Long userId : affectedUsers) {
            enqueueEvent(userId, EVENT_CONVERSATION_INDEX, message);
        }
    }

    private void enqueueEvent(Long recipientId, String eventType, MessageVO message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            jdbcTemplate.update("""
                    INSERT IGNORE INTO chat_message_outbox
                        (id, message_id, recipient_id, event_type, payload,
                         status, retry_count, next_retry_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                    """, IdWorker.getId(), Long.valueOf(message.getMessageId()), recipientId,
                    eventType, payload);
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
                SELECT id, message_id, recipient_id, event_type, payload, retry_count
                  FROM chat_message_outbox
                 WHERE (status = 0 AND next_retry_at <= CURRENT_TIMESTAMP(3))
                    OR (status = 1 AND updated_at <= ?)
                 ORDER BY id
                 LIMIT ?
                """, (rs, rowNum) -> new OutboxRow(
                rs.getLong("id"), rs.getLong("message_id"), rs.getLong("recipient_id"),
                rs.getString("event_type"), rs.getString("payload"), rs.getInt("retry_count")),
                processingDeadline, limit);
        for (OutboxRow row : rows) {
            if (jdbcTemplate.update("UPDATE chat_message_outbox SET status = 1, updated_at = CURRENT_TIMESTAMP(3) "
                    + "WHERE id = ? AND (status = 0 OR (status = 1 AND updated_at <= ?))",
                    row.id(), processingDeadline) != 1) {
                continue;
            }
            try {
                MessageVO message = objectMapper.readValue(row.payload(), MessageVO.class);
                dispatch(row, message);
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

    private void dispatch(OutboxRow row, MessageVO message) {
        switch (row.eventType()) {
            case EVENT_RECIPIENT_PUSH -> {
                recentMessageCache.append(message);
                pushRouter.route(row.recipientId(), message);
            }
            case EVENT_UNREAD_REFRESH -> {
                Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM chat_message
                         WHERE conversation_id = ? AND recipient_id = ? AND visited = 10
                        """, Long.class, message.getSessionId(), row.recipientId());
                unreadService.setCount(row.recipientId(), message.getSessionId(), count == null ? 0L : count);
            }
            case EVENT_CONVERSATION_INDEX -> {
                List<Long> failed = conversationIndexCache.touchMessageForUsers(
                        List.of(row.recipientId()), message.getSessionId(),
                        Long.valueOf(message.getMessageId()), Long.valueOf(message.getConversationSeq()),
                        message.getCreateTime());
                if (failed != null && !failed.isEmpty()) {
                    throw new IllegalStateException("Conversation index Redis update failed");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported chat outbox event: " + row.eventType());
        }
    }

    @Scheduled(fixedDelayString = "${chat.outbox.cleanup-poll-millis:60000}")
    public void cleanupSent() {
        int limit = Math.max(1, Math.min(cleanupBatchSize, 10_000));
        int retention = Math.max(1, Math.min(sentRetentionDays, 3650));
        Timestamp cutoff = Timestamp.from(Instant.now().minusSeconds(retention * 86_400L));
        int deleted = jdbcTemplate.update("""
                DELETE FROM chat_message_outbox
                 WHERE status = 2 AND updated_at < ?
                 ORDER BY id
                 LIMIT ?
                """, cutoff, limit);
        if (deleted > 0) {
            log.info("Cleaned sent chat outbox records, deleted={}, retentionDays={}", deleted, retention);
        }
    }

    private record OutboxRow(long id, long messageId, long recipientId, String eventType,
                             String payload, int retryCount) {
    }
}
