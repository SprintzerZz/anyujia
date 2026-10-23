package com.anyujia.anyujiachatservice.sharding;

import com.anyujia.anyujiachatservice.domain.entity.ChatMessage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Raw-JDBC access to the physical shard tables, one {@link JdbcTemplate} per physical MySQL
 * instance. Unlike the main {@link com.baomidou.mybatisplus.core.mapper.BaseMapper} (which is
 * bound to the primary datasource), this store picks the template by
 * {@code physicalMysqlIndex(conversationId)} and the table by {@code tableName(conversationId)},
 * so a single conversation's reads and writes always hit the same instance and table.
 *
 * <p>Each template is backed by a Seata {@code DataSourceProxy}; calls made inside a
 * {@code @GlobalTransactional} service method join the ambient global transaction as an
 * independent branch. The store itself holds no transaction state.
 */
public class ChatMessageShardStore {

    private static final String INSERT_SQL = """
            INSERT INTO %s
                (id, client_message_id, conversation_id, conversation_seq,
                 sender_id, recipient_id, type, content, status, visited, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_IGNORE_SQL = """
            INSERT IGNORE INTO %s
                (id, client_message_id, conversation_id, conversation_seq,
                 sender_id, recipient_id, type, content, status, visited, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_BY_CLIENT_ID_SQL = """
            SELECT id, client_message_id, conversation_id, conversation_seq,
                   sender_id, recipient_id, type, content, status, visited, created_at
              FROM %s
             WHERE sender_id = ? AND client_message_id = ?
             LIMIT 1
            """;
    private static final String LATEST_SQL = """
            SELECT id, client_message_id, conversation_id, conversation_seq,
                   sender_id, recipient_id, type, content, status, visited, created_at
              FROM %s
             WHERE conversation_id = ?
             ORDER BY conversation_seq DESC
             LIMIT ?
            """;
    private static final String LIST_BY_SEQ_SQL = """
            SELECT id, client_message_id, conversation_id, conversation_seq,
                   sender_id, recipient_id, type, content, status, visited, created_at
              FROM %s
             WHERE conversation_id = ? AND conversation_seq %s ?
             ORDER BY conversation_seq DESC
             LIMIT ?
            """;
    private static final String LIST_BY_ID_SQL = """
            SELECT id, client_message_id, conversation_id, conversation_seq,
                   sender_id, recipient_id, type, content, status, visited, created_at
              FROM %s
             WHERE conversation_id = ? AND id %s ?
             ORDER BY id DESC
             LIMIT ?
            """;
    private static final String SYNC_BY_SEQ_SQL = """
            SELECT id, client_message_id, conversation_id, conversation_seq,
                   sender_id, recipient_id, type, content, status, visited, created_at
              FROM %s
             WHERE conversation_id = ? AND conversation_seq > ?
             ORDER BY conversation_seq ASC
             LIMIT ?
            """;
    private static final String SYNC_BY_ID_SQL = """
            SELECT id, client_message_id, conversation_id, conversation_seq,
                   sender_id, recipient_id, type, content, status, visited, created_at
              FROM %s
             WHERE conversation_id = ? AND id > ?
             ORDER BY id ASC
             LIMIT ?
            """;
    private static final String MARK_READ_SQL = """
            UPDATE %s SET status = ?
             WHERE conversation_id = ? AND recipient_id = ? AND id IN (%s)
            """;
    private static final String MARK_VISITED_SQL = """
            UPDATE %s SET visited = ?
             WHERE conversation_id = ? AND recipient_id = ? AND visited = ?
            """;
    private static final String COUNT_UNREAD_SQL = """
            SELECT COUNT(*) FROM %s
             WHERE conversation_id = ? AND recipient_id = ? AND visited = ?
            """;
    private static final String LIST_AFTER_ID_SQL = """
            SELECT id, client_message_id, conversation_id, conversation_seq,
                   sender_id, recipient_id, type, content, status, visited, created_at
              FROM %s
             WHERE id > ?
             ORDER BY id ASC
             LIMIT ?
            """;

    private static final RowMapper<ChatMessage> ROW_MAPPER = new ChatMessageRowMapper();

    private final List<JdbcTemplate> templates;
    private final ChatMessageShardRouter shardRouter;

    public ChatMessageShardStore(List<DataSource> shardDataSources, ChatMessageShardRouter shardRouter) {
        this(shardDataSources.stream().map(JdbcTemplate::new).toArray(JdbcTemplate[]::new), shardRouter);
    }

    /** Test seam: inject the per-instance templates directly. */
    ChatMessageShardStore(JdbcTemplate[] templates, ChatMessageShardRouter shardRouter) {
        this.shardRouter = shardRouter;
        this.templates = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(templates)));
    }

    public int insert(ChatMessage message) {
        if (message.getId() == null) {
            message.setId(IdWorker.getId());
        }
        String table = tableFor(message.getConversationId());
        return templateFor(message.getConversationId()).update(String.format(INSERT_SQL, table),
                insertArgs(message));
    }

    /** Idempotent insert used by the backfill job (skips rows already present). */
    public int insertIgnore(ChatMessage message) {
        String table = tableFor(message.getConversationId());
        return templateFor(message.getConversationId()).update(String.format(INSERT_IGNORE_SQL, table),
                insertArgs(message));
    }

    /** Row count of the physical shard table named by {@code physicalShard}. */
    public long countInShard(int physicalShard) {
        String table = shardRouter.tableNameForShard(physicalShard);
        int instanceIndex = shardRouter.physicalMysqlIndexForShard(physicalShard);
        Long count = templates.get(instanceIndex).queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0L : count;
    }

    private Object[] insertArgs(ChatMessage message) {
        return new Object[]{
                message.getId(),
                message.getClientMessageId(),
                message.getConversationId(),
                message.getConversationSeq(),
                message.getSenderId(),
                message.getRecipientId(),
                message.getType(),
                message.getContent(),
                message.getStatus(),
                message.getVisited(),
                toTimestamp(message.getCreatedAt())};
    }

    public ChatMessage findByClientId(Long conversationId, Long senderId, String clientMessageId) {
        String table = tableFor(conversationId);
        List<ChatMessage> rows = templateFor(conversationId).query(
                String.format(FIND_BY_CLIENT_ID_SQL, table), ROW_MAPPER, senderId, clientMessageId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Latest messages for a conversation, newest first. */
    public List<ChatMessage> listLatest(Long conversationId, int count) {
        String table = tableFor(conversationId);
        return templateFor(conversationId).query(
                String.format(LATEST_SQL, table), ROW_MAPPER, conversationId, count);
    }

    /** Paginated back-fill by conversation sequence; newest first. */
    public List<ChatMessage> listBySeq(Long conversationId, Long cursor, boolean includeCursor, int count) {
        String operator = includeCursor ? "<=" : "<";
        String table = tableFor(conversationId);
        return templateFor(conversationId).query(
                String.format(LIST_BY_SEQ_SQL, table, operator), ROW_MAPPER, conversationId, cursor, count);
    }

    /** Paginated back-fill by message id; newest first. */
    public List<ChatMessage> listById(Long conversationId, Long cursor, boolean includeCursor, int count) {
        String operator = includeCursor ? "<=" : "<";
        String table = tableFor(conversationId);
        return templateFor(conversationId).query(
                String.format(LIST_BY_ID_SQL, table, operator), ROW_MAPPER, conversationId, cursor, count);
    }

    /** Messages newer than the given sequence, oldest first. */
    public List<ChatMessage> syncBySeq(Long conversationId, Long afterSeq, int count) {
        String table = tableFor(conversationId);
        return templateFor(conversationId).query(
                String.format(SYNC_BY_SEQ_SQL, table), ROW_MAPPER, conversationId, afterSeq, count);
    }

    /** Messages newer than the given id, oldest first. */
    public List<ChatMessage> syncById(Long conversationId, Long afterId, int count) {
        String table = tableFor(conversationId);
        return templateFor(conversationId).query(
                String.format(SYNC_BY_ID_SQL, table), ROW_MAPPER, conversationId, afterId, count);
    }

    /** The single newest message for a conversation, or {@code null}. */
    public ChatMessage selectLatest(Long conversationId) {
        List<ChatMessage> rows = listLatest(conversationId, 1);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Lists messages in one physical shard after a durable reconciliation cursor. */
    public List<ChatMessage> listAfterId(int physicalShard, long afterId, int limit) {
        String table = shardRouter.tableNameForShard(physicalShard);
        return templates.get(shardRouter.physicalMysqlIndexForShard(physicalShard)).query(
                String.format(LIST_AFTER_ID_SQL, table), ROW_MAPPER, afterId, limit);
    }

    public int markRead(Long conversationId, Long recipientId, List<Long> ids, int status) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String table = tableFor(conversationId);
        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>(ids.size() + 3);
        args.add(status);
        args.add(conversationId);
        args.add(recipientId);
        args.addAll(ids);
        return templateFor(conversationId).update(
                String.format(MARK_READ_SQL, table, placeholders), args.toArray());
    }

    public int markVisited(Long conversationId, Long recipientId, int fromVisited, int toVisited) {
        String table = tableFor(conversationId);
        return templateFor(conversationId).update(
                String.format(MARK_VISITED_SQL, table), toVisited, conversationId, recipientId, fromVisited);
    }

    public long countUnread(Long conversationId, Long recipientId, int visited) {
        String table = tableFor(conversationId);
        Long count = templateFor(conversationId).queryForObject(
                String.format(COUNT_UNREAD_SQL, table), Long.class, conversationId, recipientId, visited);
        return count == null ? 0L : count;
    }

    public int instanceCount() {
        return templates.size();
    }

    /** The per-instance templates, index = physical MySQL index (for pool observation). */
    public List<JdbcTemplate> templates() {
        return templates;
    }

    String tableFor(Long conversationId) {
        return shardRouter.tableName(conversationId);
    }

    JdbcTemplate templateFor(Long conversationId) {
        int index = shardRouter.physicalMysqlIndex(conversationId);
        if (index < 0 || index >= templates.size()) {
            throw new IllegalStateException("No datasource for physical MySQL index " + index);
        }
        return templates.get(index);
    }

    private static Timestamp toTimestamp(LocalDateTime time) {
        return time == null ? null : Timestamp.valueOf(time);
    }

    private static final class ChatMessageRowMapper implements RowMapper<ChatMessage> {
        @Override
        public ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            ChatMessage message = new ChatMessage();
            message.setId(rs.getLong("id"));
            message.setClientMessageId(rs.getString("client_message_id"));
            message.setConversationId(rs.getLong("conversation_id"));
            message.setConversationSeq(rs.getLong("conversation_seq"));
            message.setSenderId(rs.getLong("sender_id"));
            message.setRecipientId(rs.getLong("recipient_id"));
            message.setType(rs.getInt("type"));
            message.setContent(rs.getString("content"));
            message.setStatus(rs.getInt("status"));
            message.setVisited(rs.getInt("visited"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            message.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
            return message;
        }
    }
}
