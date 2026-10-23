package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiachatapi.constants.ChatConstants;
import com.anyujia.anyujiachatservice.domain.entity.ChatMessage;
import com.anyujia.anyujiachatservice.cache.RecentMessageCache;
import com.anyujia.anyujiachatservice.cache.ChatConversationIndexCache;
import com.anyujia.anyujiachatservice.service.unread.ChatUnreadService;
import com.anyujia.anyujiachatservice.sharding.ChatMessageShardRouter;
import com.anyujia.anyujiachatservice.sharding.ChatMessageShardStore;
import com.anyujia.anyujiachatservice.sharding.ChatMessageStorageMode;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
    private final ChatMessageShardRouter shardRouter;

    @Value("${chat.message-sharding.mode:LEGACY_ONLY}")
    private ChatMessageStorageMode storageMode = ChatMessageStorageMode.LEGACY_ONLY;

    @Value("${chat.message-sharding.physical-enabled:false}")
    private boolean physicalEnabled;

    @Autowired(required = false)
    private ChatMessageShardStore shardStore;

    @Value("${chat.outbox.batch-size:100}")
    private int batchSize;

    @Value("${chat.outbox.worker-threads:4}")
    private int workerThreads;

    @Value("${chat.outbox.retry-delay-millis:1000}")
    private long retryDelayMillis;

    @Value("${chat.outbox.max-retries:10}")
    private int maxRetries;

    @Value("${chat.outbox.processing-timeout-millis:60000}")
    private long processingTimeoutMillis;

    @Value("${chat.outbox.sent-retention-days:7}")
    private int sentRetentionDays;

    @Value("${chat.outbox.cleanup-batch-size:1000}")
    private int cleanupBatchSize;

    @Value("${chat.outbox.enqueue-threads:4}")
    private int enqueueThreads;

    @Value("${chat.outbox.enqueue-queue-capacity:20000}")
    private int enqueueQueueCapacity;

    @Value("${chat.outbox.reconcile-enabled:true}")
    private boolean reconcileEnabled;

    @Value("${chat.outbox.reconcile-batch-size:500}")
    private int reconcileBatchSize;

    /**
     * Re-read a bounded suffix on every reconciliation pass. A message transaction can become
     * visible after a previous pass has advanced the durable cursor; without an overlap window
     * that late commit would never be inspected again. Inserts are idempotent by the outbox
     * unique key, so the trade-off is extra bounded reads rather than duplicate side effects.
     */
    @Value("${chat.outbox.reconcile-overlap-messages:100}")
    private int reconcileOverlapMessages;

    private ExecutorService dispatchExecutor;
    private ThreadPoolExecutor enqueueExecutor;

    @PostConstruct
    void init() {
        this.dispatchExecutor = Executors.newFixedThreadPool(Math.max(1, workerThreads));
        // Off-request-thread outbox writer. The queue is bounded and overflow runs on the caller
        // (CallerRunsPolicy), so a backlog applies back-pressure to the send path instead of
        // dropping events or growing the heap without limit.
        int enqueueSize = Math.max(1, enqueueThreads);
        this.enqueueExecutor = new ThreadPoolExecutor(enqueueSize, enqueueSize,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, enqueueQueueCapacity)),
                namedThreadFactory("chat-outbox-enqueue-"),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @PreDestroy
    void shutdown() {
        if (dispatchExecutor != null) {
            dispatchExecutor.shutdownNow();
        }
        if (enqueueExecutor != null) {
            enqueueExecutor.shutdown();
            try {
                if (!enqueueExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    enqueueExecutor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                enqueueExecutor.shutdownNow();
            }
        }
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public void enqueue(Long recipientId, MessageVO message) {
        // Single INSERT IGNORE; participates in the caller's @GlobalTransactional (Seata) branch.
        // No local @Transactional here: it would force autoCommit=false and defer the main
        // datasource's branch registration past the global commit.
        enqueueBatch(List.of(new OutboxTarget(recipientId, EVENT_RECIPIENT_PUSH)), message);
    }

    public void enqueueMessageEvents(Long recipientId, List<Long> affectedUsers,
                                     boolean incrementUnread, MessageVO message) {
        List<OutboxTarget> targets = new ArrayList<>(1 + (incrementUnread ? 1 : 0) + affectedUsers.size());
        targets.add(new OutboxTarget(recipientId, EVENT_RECIPIENT_PUSH));
        if (incrementUnread && recipientId != null) {
            targets.add(new OutboxTarget(recipientId, EVENT_UNREAD_REFRESH));
        }
        for (Long userId : affectedUsers) {
            targets.add(new OutboxTarget(userId, EVENT_CONVERSATION_INDEX));
        }
        enqueueBatch(targets, message);
    }

    public void enqueueDerivedEvents(List<Long> affectedUsers, boolean incrementUnread,
                                     Long unreadUserId, MessageVO message) {
        List<OutboxTarget> targets = new ArrayList<>((incrementUnread ? 1 : 0) + affectedUsers.size());
        if (incrementUnread && unreadUserId != null) {
            targets.add(new OutboxTarget(unreadUserId, EVENT_UNREAD_REFRESH));
        }
        for (Long userId : affectedUsers) {
            targets.add(new OutboxTarget(userId, EVENT_CONVERSATION_INDEX));
        }
        enqueueBatch(targets, message);
    }

    /**
     * Fire-and-forget variant used by the message send path. The message row is already durable in
     * its shard table when this is called, so a delayed or failed outbox write only delays
     * push/unread/index propagation; it never loses the message, and the shard remains the source
     * of truth for reconciliation.
     */
    public void enqueueMessageEventsAsync(Long recipientId, List<Long> affectedUsers,
                                          boolean incrementUnread, MessageVO message) {
        submitAsync(() -> enqueueMessageEvents(recipientId, affectedUsers, incrementUnread, message));
    }

    public void enqueueDerivedEventsAsync(List<Long> affectedUsers, boolean incrementUnread,
                                          Long unreadUserId, MessageVO message) {
        submitAsync(() -> enqueueDerivedEvents(affectedUsers, incrementUnread, unreadUserId, message));
    }

    private void submitAsync(Runnable task) {
        if (enqueueExecutor == null) {
            task.run();
            return;
        }
        try {
            enqueueExecutor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException ex) {
                    log.error("Chat outbox enqueue failed after message persisted to shard", ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            log.error("Chat outbox enqueue rejected because the executor is shutting down", ex);
        }
    }

    /**
     * Writes all outbox events for one message in a single multi-row INSERT. The previous
     * one-INSERT-per-event implementation opened one Hikari connection (and therefore one Seata
     * branch) per event, so a normal message produced 4 branches on the main datasource and
     * exhausted the 30-connection pool. One statement = one branch.
     */
    private void enqueueBatch(List<OutboxTarget> targets, MessageVO message) {
        if (targets.isEmpty()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(message);
            long messageId = Long.parseLong(message.getMessageId());
            StringBuilder sql = new StringBuilder(
                    "INSERT IGNORE INTO chat_message_outbox "
                            + "(id, message_id, recipient_id, event_type, payload, "
                            + "status, retry_count, next_retry_at, created_at, updated_at) VALUES ");
            List<Object> args = new ArrayList<>(targets.size() * 5);
            for (int i = 0; i < targets.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("(?, ?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))");
                OutboxTarget target = targets.get(i);
                args.add(IdWorker.getId());
                args.add(messageId);
                args.add(target.recipientId());
                args.add(target.eventType());
                args.add(payload);
            }
            jdbcTemplate.update(sql.toString(), args.toArray());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize chat outbox payload", ex);
        }
    }

    private record OutboxTarget(Long recipientId, String eventType) {
    }

    @Scheduled(fixedDelayString = "${chat.outbox.poll-millis:1000}")
    public void publishPending() {
        int limit = Math.max(1, Math.min(batchSize, 500));
        Timestamp processingDeadline = Timestamp.from(Instant.now().minusMillis(
                Math.max(1_000L, processingTimeoutMillis)));
        String claimToken = UUID.randomUUID().toString();
        // Two single-predicate claims instead of one `WHERE a OR b`. The OR form cannot use either
        // index, so each poll scanned the whole outbox (tens of thousands of rows) under row/gap
        // locks, which serialized every concurrent outbox INSERT on the send path. Each statement
        // below now walks exactly one index and stops at LIMIT:
        //   pending  -> idx_chat_outbox_pending (status, next_retry_at, id)
        //   inflight -> idx_chat_outbox_sent_cleanup (status, updated_at, id)
        int claimed = jdbcTemplate.update("""
                UPDATE chat_message_outbox
                   SET status = 1, claim_token = ?, updated_at = CURRENT_TIMESTAMP(3)
                 WHERE status = 0 AND next_retry_at <= CURRENT_TIMESTAMP(3)
                 ORDER BY next_retry_at, id
                 LIMIT ?
                """, claimToken, limit);
        if (claimed < limit) {
            // FORCE INDEX pins the reclaim to the (status, updated_at, id) index. Without it the
            // optimizer prefers idx_chat_outbox_pending and degrades to a status-only scan.
            claimed += jdbcTemplate.update("""
                    UPDATE chat_message_outbox FORCE INDEX (idx_chat_outbox_sent_cleanup)
                       SET status = 1, claim_token = ?, updated_at = CURRENT_TIMESTAMP(3)
                     WHERE status = 1 AND updated_at <= ?
                     ORDER BY updated_at, id
                     LIMIT ?
                    """, claimToken, processingDeadline, limit - claimed);
        }
        if (claimed == 0) {
            return;
        }
        List<OutboxRow> rows = jdbcTemplate.query("""
                SELECT id, message_id, recipient_id, event_type, payload, retry_count
                  FROM chat_message_outbox
                 WHERE status = 1 AND claim_token = ?
                 ORDER BY id
                """, (rs, rowNum) -> new OutboxRow(
                rs.getLong("id"), rs.getLong("message_id"), rs.getLong("recipient_id"),
                rs.getString("event_type"), rs.getString("payload"), rs.getInt("retry_count")),
                claimToken);
        if (rows.isEmpty()) {
            return;
        }

        // Merge derived events: recipient_push is per-message (never merged);
        // unread_refresh and conversation_index are idempotent per (recipient, conversation),
        // so only the latest row in this batch needs to be dispatched. Older duplicates are
        // marked sent without any dispatch work.
        Map<String, OutboxRow> kept = new LinkedHashMap<>();
        List<Long> skippedIds = new ArrayList<>();
        for (OutboxRow row : rows) {
            if (EVENT_RECIPIENT_PUSH.equals(row.eventType())) {
                kept.put(row.eventType() + ":" + row.id(), row);
                continue;
            }
            Long conversationId = readConversationId(row);
            if (conversationId == null) {
                retry(row, claimToken, new IllegalArgumentException("Outbox payload has no conversation ID"));
                continue;
            }
            String key = row.eventType() + ":" + row.recipientId() + ":" + conversationId;
            OutboxRow existing = kept.get(key);
            if (existing == null || row.id() > existing.id()) {
                if (existing != null) {
                    skippedIds.add(existing.id());
                }
                kept.put(key, row);
            } else {
                skippedIds.add(row.id());
            }
        }

        if (!skippedIds.isEmpty()) {
            markDone(skippedIds, claimToken);
        }

        List<Long> successfulIds = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> futures = new ArrayList<>(kept.size());
        for (OutboxRow row : kept.values()) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    MessageVO message = objectMapper.readValue(row.payload(), MessageVO.class);
                    dispatch(row, message);
                    successfulIds.add(row.id());
                } catch (Exception ex) {
                    retry(row, claimToken, ex);
                }
            }, dispatchExecutor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (!successfulIds.isEmpty()) {
            markDone(successfulIds, claimToken);
        }
    }

    /**
     * Rebuilds missing derived events from the message source of truth. Message persistence and
     * outbox insertion are intentionally independent in SHARD_ONLY mode, so this job closes the
     * crash/failure window between those two writes. Cursor advancement happens only after every
     * message in the batch has been repaired; duplicate events are idempotent and terminally failed
     * events are reset to pending for another bounded retry cycle.
     */
    @Scheduled(fixedDelayString = "${chat.outbox.reconcile-poll-millis:10000}")
    public void reconcileMissingEvents() {
        if (!reconcileEnabled) {
            return;
        }
        int limit = Math.max(1, Math.min(reconcileBatchSize, 5_000));
        reviveTerminalEvents(limit);
        if (physicalMessageRouting()) {
            for (int physicalShard = 0; physicalShard < shardRouter.physicalShardCount(); physicalShard++) {
                reconcilePhysicalShard(physicalShard, limit);
            }
            return;
        }
        reconcileLegacy(limit);
    }

    private void reconcileLegacy(int limit) {
        String source = "legacy";
        long cursor = loadReconcileCursor(source);
        long scanCursor = overlapCursor(cursor);
        List<ReconciliationMessage> messages = jdbcTemplate.query("""
                SELECT id, client_message_id, conversation_id, conversation_seq,
                       sender_id, recipient_id, type, content, status, visited, created_at
                  FROM chat_message
                 WHERE id > ?
                 ORDER BY id ASC
                 LIMIT ?
                """, (rs, rowNum) -> toReconciliationMessage(new ChatMessageRow(rs)), scanCursor, limit);
        repairBatch(source, cursor, messages);
    }

    private void reconcilePhysicalShard(int physicalShard, int limit) {
        String source = "shard-" + physicalShard;
        long cursor = loadReconcileCursor(source);
        long scanCursor = overlapCursor(cursor);
        List<ReconciliationMessage> messages = shardStore.listAfterId(physicalShard, scanCursor, limit).stream()
                .map(this::toReconciliationMessage)
                .toList();
        repairBatch(source, cursor, messages);
    }

    private long overlapCursor(long cursor) {
        return Math.max(0L, cursor - Math.max(0, reconcileOverlapMessages));
    }

    private void repairBatch(String source, long cursor, List<ReconciliationMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }
        long lastId = cursor;
        for (ReconciliationMessage reconciliationMessage : messages) {
            // Do not move the cursor past a failed row. The next run retries it and all later rows.
            enqueueReconciledEvents(reconciliationMessage);
            lastId = Long.parseLong(reconciliationMessage.message().getMessageId());
        }
        advanceReconcileCursor(source, lastId);
        log.info("Reconciled chat outbox events, source={}, messages={}, cursor={}",
                source, messages.size(), lastId);
    }

    private void enqueueReconciledEvents(ReconciliationMessage reconciliationMessage) {
        MessageVO message = reconciliationMessage.message();
        Long recipientId = reconciliationMessage.recipientId();
        List<OutboxTarget> targets = new ArrayList<>();
        // Agent-originated messages have the agent as sender and a real tenant recipient. A
        // tenant-originated message to the agent only refreshes the tenant's conversation index;
        // the agent reply path is asynchronous and must not receive a user push event.
        boolean sentToAgent = recipientId != null && recipientId == ChatConstants.AGENT_USER_ID;
        if (sentToAgent) {
            targets.add(new OutboxTarget(reconciliationMessage.senderId(), EVENT_CONVERSATION_INDEX));
            enqueueReconciledBatch(targets, message);
            return;
        }
        boolean sentByAgent = reconciliationMessage.senderId() != null
                && reconciliationMessage.senderId() == ChatConstants.AGENT_USER_ID;
        targets.add(new OutboxTarget(recipientId, EVENT_RECIPIENT_PUSH));
        if (!sentByAgent) {
            targets.add(new OutboxTarget(recipientId, EVENT_UNREAD_REFRESH));
            targets.add(new OutboxTarget(reconciliationMessage.senderId(), EVENT_CONVERSATION_INDEX));
        }
        targets.add(new OutboxTarget(recipientId, EVENT_CONVERSATION_INDEX));
        enqueueReconciledBatch(targets, message);
    }

    private void enqueueReconciledBatch(List<OutboxTarget> targets, MessageVO message) {
        if (targets.isEmpty()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(message);
            long messageId = Long.parseLong(message.getMessageId());
            StringBuilder sql = new StringBuilder(
                    "INSERT INTO chat_message_outbox "
                            + "(id, message_id, recipient_id, event_type, payload, "
                            + "status, retry_count, next_retry_at, created_at, updated_at) VALUES ");
            List<Object> args = new ArrayList<>(targets.size() * 5);
            for (int i = 0; i < targets.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("(?, ?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))");
                OutboxTarget target = targets.get(i);
                args.add(IdWorker.getId());
                args.add(messageId);
                args.add(target.recipientId());
                args.add(target.eventType());
                args.add(payload);
            }
            sql.append(" ON DUPLICATE KEY UPDATE "
                    + "payload = IF(status = 3, VALUES(payload), payload), "
                    + "retry_count = IF(status = 3, 0, retry_count), "
                    + "claim_token = IF(status = 3, NULL, claim_token), "
                    + "next_retry_at = IF(status = 3, CURRENT_TIMESTAMP(3), next_retry_at), "
                    + "updated_at = IF(status = 3, CURRENT_TIMESTAMP(3), updated_at), "
                    + "status = IF(status = 3, 0, status)");
            jdbcTemplate.update(sql.toString(), args.toArray());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize reconciled chat outbox payload", ex);
        }
    }

    private void reviveTerminalEvents(int limit) {
        int revived = jdbcTemplate.update("""
                UPDATE chat_message_outbox
                   SET status = 0, retry_count = 0, claim_token = NULL,
                       next_retry_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)
                 WHERE status = 3
                 ORDER BY updated_at, id
                 LIMIT ?
                """, limit);
        if (revived > 0) {
            log.warn("Revived terminal chat outbox events for reconciliation, count={}", revived);
        }
    }

    private boolean physicalMessageRouting() {
        return physicalEnabled && storageMode != ChatMessageStorageMode.LEGACY_ONLY && shardStore != null;
    }

    private long loadReconcileCursor(String source) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO chat_outbox_reconcile_cursor
                    (source_key, last_message_id, updated_at)
                VALUES (?, 0, CURRENT_TIMESTAMP(3))
                """, source);
        Long cursor = jdbcTemplate.queryForObject("""
                SELECT last_message_id
                  FROM chat_outbox_reconcile_cursor
                 WHERE source_key = ?
                """, Long.class, source);
        return cursor == null ? 0L : cursor;
    }

    private void advanceReconcileCursor(String source, long cursor) {
        jdbcTemplate.update("""
                UPDATE chat_outbox_reconcile_cursor
                   SET last_message_id = ?, updated_at = CURRENT_TIMESTAMP(3)
                 WHERE source_key = ? AND last_message_id < ?
                """, cursor, source, cursor);
    }

    private MessageVO toMessageVO(ChatMessage message) {
        MessageVO vo = new MessageVO();
        vo.setMessageId(String.valueOf(message.getId()));
        vo.setClientMessageId(message.getClientMessageId());
        vo.setConversationSeq(String.valueOf(message.getConversationSeq()));
        vo.setSessionId(message.getConversationId());
        vo.setFromId(message.getSenderId());
        vo.setContent(message.getContent());
        vo.setType(message.getType());
        vo.setStatus(message.getStatus());
        vo.setVisited(message.getVisited());
        vo.setCreateTime(message.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        return vo;
    }

    private ReconciliationMessage toReconciliationMessage(ChatMessage message) {
        return new ReconciliationMessage(toMessageVO(message), message.getSenderId(), message.getRecipientId());
    }

    private MessageVO toMessageVO(ChatMessageRow row) {
        MessageVO vo = new MessageVO();
        vo.setMessageId(String.valueOf(row.id));
        vo.setClientMessageId(row.clientMessageId);
        vo.setConversationSeq(String.valueOf(row.conversationSeq));
        vo.setSessionId(row.conversationId);
        vo.setFromId(row.senderId);
        vo.setContent(row.content);
        vo.setType(row.type);
        vo.setStatus(row.status);
        vo.setVisited(row.visited);
        vo.setCreateTime(row.createdAt.toInstant().toEpochMilli());
        return vo;
    }

    private ReconciliationMessage toReconciliationMessage(ChatMessageRow row) {
        return new ReconciliationMessage(toMessageVO(row), row.senderId, row.recipientId);
    }

    private record ReconciliationMessage(MessageVO message, Long senderId, Long recipientId) {
    }

    private record ChatMessageRow(long id, String clientMessageId, long conversationId,
                                  long conversationSeq, long senderId, long recipientId,
                                  int type, String content, int status, int visited,
                                  Timestamp createdAt) {
        private ChatMessageRow(java.sql.ResultSet rs) throws java.sql.SQLException {
            this(rs.getLong("id"), rs.getString("client_message_id"),
                    rs.getLong("conversation_id"), rs.getLong("conversation_seq"),
                    rs.getLong("sender_id"), rs.getLong("recipient_id"), rs.getInt("type"),
                    rs.getString("content"), rs.getInt("status"), rs.getInt("visited"),
                    rs.getTimestamp("created_at"));
        }
    }

    private Long readConversationId(OutboxRow row) {
        try {
            return objectMapper.readValue(row.payload(), MessageVO.class).getSessionId();
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private void markDone(List<Long> ids, String claimToken) {
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        jdbcTemplate.update("UPDATE chat_message_outbox SET status = 2, updated_at = CURRENT_TIMESTAMP(3) "
                + "WHERE id IN (" + placeholders + ") AND status = 1 AND claim_token = ?",
                append(ids, claimToken));
    }

    private void retry(OutboxRow row, String claimToken, Exception ex) {
        int attempts = row.retryCount() + 1;
        if (attempts >= Math.max(1, maxRetries)) {
            jdbcTemplate.update("""
                    UPDATE chat_message_outbox
                       SET status = 3, retry_count = retry_count + 1,
                           claim_token = NULL, updated_at = CURRENT_TIMESTAMP(3)
                     WHERE id = ? AND status = 1 AND claim_token = ?
                    """, row.id(), claimToken);
            log.error("Chat outbox event exhausted retries, outboxId={}, retryCount={}",
                    row.id(), attempts, ex);
            return;
        }
        long delay = Math.min(retryDelayMillis * (1L << Math.min(row.retryCount(), 10)), 300_000L);
        jdbcTemplate.update("""
                UPDATE chat_message_outbox
                   SET status = 0, retry_count = retry_count + 1,
                       claim_token = NULL, next_retry_at = ?, updated_at = CURRENT_TIMESTAMP(3)
                 WHERE id = ? AND status = 1 AND claim_token = ?
                """, Timestamp.from(Instant.now().plusMillis(delay)), row.id(), claimToken);
        log.warn("Chat outbox publish failed, outboxId={}, retryCount={}", row.id(),
                row.retryCount() + 1, ex);
    }

    private void dispatch(OutboxRow row, MessageVO message) {
        switch (row.eventType()) {
            case EVENT_RECIPIENT_PUSH -> {
                recentMessageCache.append(message);
                pushRouter.route(row.recipientId(), message);
            }
            case EVENT_UNREAD_REFRESH -> {
                long count;
                if (physicalEnabled && storageMode != ChatMessageStorageMode.LEGACY_ONLY && shardStore != null) {
                    count = shardStore.countUnread(message.getSessionId(), row.recipientId(), 10);
                } else {
                    String messageTable = storageMode == ChatMessageStorageMode.LEGACY_ONLY
                            ? "chat_message" : shardRouter.tableName(message.getSessionId());
                    Long countValue = jdbcTemplate.queryForObject("""
                            SELECT COUNT(*) FROM %s
                             WHERE conversation_id = ? AND recipient_id = ? AND visited = 10
                            """.formatted(messageTable), Long.class, message.getSessionId(), row.recipientId());
                    count = countValue == null ? 0L : countValue;
                }
                unreadService.setCount(row.recipientId(), message.getSessionId(), count);
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

    private Object[] append(List<Long> ids, String claimToken) {
        Object[] parameters = new Object[ids.size() + 1];
        for (int i = 0; i < ids.size(); i++) {
            parameters[i] = ids.get(i);
        }
        parameters[ids.size()] = claimToken;
        return parameters;
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
