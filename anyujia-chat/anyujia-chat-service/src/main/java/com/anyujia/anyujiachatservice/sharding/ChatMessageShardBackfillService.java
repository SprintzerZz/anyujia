package com.anyujia.anyujiachatservice.sharding;

import com.anyujia.anyujiachatservice.domain.entity.ChatMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Restartable, low-lock backfill from the legacy table into shard tables. In single-database
 * mode it writes the 32 logical shard tables on the main MySQL; when
 * {@code chat.message-sharding.physical-enabled} is on it routes each row to its physical MySQL
 * instance via {@link ChatMessageShardStore}. Disabled by default; enable only after the shard
 * schema exists.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chat.message-sharding.backfill", name = "enabled",
        havingValue = "true")
public class ChatMessageShardBackfillService {
    private static final String PROGRESS_SQL =
            "SELECT last_message_id FROM chat_message_shard_backfill_progress WHERE id = 1";
    private static final String UPDATE_PROGRESS_SQL =
            "UPDATE chat_message_shard_backfill_progress SET last_message_id = ? WHERE id = 1";
    private static final String SOURCE_SQL = """
            SELECT id, client_message_id, conversation_id, conversation_seq,
                   sender_id, recipient_id, type, content, status, visited, created_at
              FROM chat_message
             WHERE id > ?
             ORDER BY id
             LIMIT ?
            """;
    private static final String INSERT_SQL = """
            INSERT IGNORE INTO %s
                (id, client_message_id, conversation_id, conversation_seq,
                 sender_id, recipient_id, type, content, status, visited, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ChatMessageShardRouter shardRouter;
    private final ObjectProvider<ChatMessageShardStore> shardStoreProvider;

    @Value("${chat.message-sharding.backfill.batch-size:1000}")
    private int batchSize;

    @Value("${chat.message-sharding.physical-enabled:false}")
    private boolean physicalEnabled;

    @Scheduled(fixedDelayString = "${chat.message-sharding.backfill.fixed-delay-millis:10000}")
    public void migrateBatch() {
        Long cursor = jdbcTemplate.queryForObject(PROGRESS_SQL, Long.class);
        if (cursor == null) {
            cursor = 0L;
        }
        int size = Math.max(1, Math.min(batchSize, 10_000));
        List<BackfillRow> rows = jdbcTemplate.query(SOURCE_SQL, new BackfillRowMapper(), cursor, size);
        if (rows.isEmpty()) {
            return;
        }

        ChatMessageShardStore store = physicalEnabled ? shardStoreProvider.getIfAvailable() : null;
        if (store != null) {
            for (BackfillRow row : rows) {
                store.insertIgnore(toMessage(row));
            }
        } else {
            Map<String, List<BackfillRow>> byTable = new LinkedHashMap<>();
            for (BackfillRow row : rows) {
                String table = shardRouter.tableName(row.conversationId());
                byTable.computeIfAbsent(table, ignored -> new java.util.ArrayList<>()).add(row);
            }
            for (Map.Entry<String, List<BackfillRow>> entry : byTable.entrySet()) {
                jdbcTemplate.batchUpdate(String.format(INSERT_SQL, entry.getKey()),
                        entry.getValue(), entry.getValue().size(), (statement, row) -> {
                            statement.setLong(1, row.id());
                            statement.setString(2, row.clientMessageId());
                            statement.setLong(3, row.conversationId());
                            statement.setLong(4, row.conversationSeq());
                            statement.setLong(5, row.senderId());
                            statement.setLong(6, row.recipientId());
                            statement.setInt(7, row.type());
                            statement.setString(8, row.content());
                            statement.setInt(9, row.status());
                            statement.setInt(10, row.visited());
                            statement.setTimestamp(11, row.createdAt());
                        });
            }
        }
        long nextCursor = rows.get(rows.size() - 1).id();
        jdbcTemplate.update(UPDATE_PROGRESS_SQL, nextCursor);
        log.info("Backfilled {} chat messages through id {}", rows.size(), nextCursor);
    }

    private static ChatMessage toMessage(BackfillRow row) {
        ChatMessage message = new ChatMessage();
        message.setId(row.id());
        message.setClientMessageId(row.clientMessageId());
        message.setConversationId(row.conversationId());
        message.setConversationSeq(row.conversationSeq());
        message.setSenderId(row.senderId());
        message.setRecipientId(row.recipientId());
        message.setType(row.type());
        message.setContent(row.content());
        message.setStatus(row.status());
        message.setVisited(row.visited());
        message.setCreatedAt(row.createdAt().toLocalDateTime());
        return message;
    }

    private record BackfillRow(long id, String clientMessageId, long conversationId,
                               long conversationSeq, long senderId, long recipientId,
                               int type, String content, int status, int visited,
                               Timestamp createdAt) {
    }

    private static final class BackfillRowMapper implements RowMapper<BackfillRow> {
        @Override
        public BackfillRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new BackfillRow(
                    rs.getLong("id"),
                    rs.getString("client_message_id"),
                    rs.getLong("conversation_id"),
                    rs.getLong("conversation_seq"),
                    rs.getLong("sender_id"),
                    rs.getLong("recipient_id"),
                    rs.getInt("type"),
                    rs.getString("content"),
                    rs.getInt("status"),
                    rs.getInt("visited"),
                    rs.getTimestamp("created_at"));
        }
    }
}
