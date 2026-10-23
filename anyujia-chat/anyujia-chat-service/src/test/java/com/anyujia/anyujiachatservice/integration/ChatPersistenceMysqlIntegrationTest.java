package com.anyujia.anyujiachatservice.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiachatservice.cache.ChatConversationIndexCache;
import com.anyujia.anyujiachatservice.cache.RecentMessageCache;
import com.anyujia.anyujiachatservice.mapper.ChatConversationMapper;
import com.anyujia.anyujiachatservice.realtime.ChatMessageOutboxService;
import com.anyujia.anyujiachatservice.realtime.ChatMessagePublisher;
import com.anyujia.anyujiachatservice.service.unread.ChatUnreadService;
import com.anyujia.anyujiachatservice.sharding.ChatMessageShardRouter;
import com.anyujia.anyujiachatservice.sharding.ChatMessageStorageMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ChatPersistenceMysqlIntegrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.2")
            .withDatabaseName("chat_test")
            .withUsername("chat")
            .withPassword("chat");

    private JdbcTemplate jdbcTemplate;
    private ChatMessageOutboxService workerOne;
    private ChatMessageOutboxService workerTwo;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS chat_message_outbox");
        jdbcTemplate.execute("DROP TABLE IF EXISTS chat_conversation");
        jdbcTemplate.execute("DROP TABLE IF EXISTS chat_message_s07");
        jdbcTemplate.execute("""
                CREATE TABLE chat_message_outbox (
                    id BIGINT PRIMARY KEY, message_id BIGINT NOT NULL, recipient_id BIGINT NOT NULL,
                    event_type VARCHAR(32) NOT NULL, payload JSON NOT NULL, status TINYINT NOT NULL DEFAULT 0,
                    retry_count INT NOT NULL DEFAULT 0, claim_token VARCHAR(36) NULL,
                    next_retry_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                    KEY idx_pending(status, next_retry_at, id), KEY idx_claim(claim_token, id),
                    KEY idx_chat_outbox_sent_cleanup(status, updated_at, id)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("""
                CREATE TABLE chat_conversation (
                    id BIGINT PRIMARY KEY, next_message_seq BIGINT NOT NULL DEFAULT 0,
                    last_message_id BIGINT NULL, last_message_seq BIGINT NOT NULL DEFAULT 0,
                    last_message_time BIGINT NULL, updated_at DATETIME(3) NULL
                ) ENGINE=InnoDB
                """);
    }

    @AfterEach
    void tearDown() {
        shutdown(workerOne);
        shutdown(workerTwo);
    }

    @Test
    void concurrentWorkersClaimEachOutboxRowOnlyOnce() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        for (long id = 1; id <= 40; id++) {
            MessageVO message = new MessageVO();
            message.setMessageId(String.valueOf(id));
            message.setSessionId(7L);
            jdbcTemplate.update("""
                    INSERT INTO chat_message_outbox
                      (id, message_id, recipient_id, event_type, payload)
                    VALUES (?, ?, 20, 'recipient_push', ?)
                    """, id, id, mapper.writeValueAsString(message));
        }

        Map<String, AtomicInteger> deliveries = new ConcurrentHashMap<>();
        ChatMessagePublisher publisher = (recipientId, message) -> deliveries
                .computeIfAbsent(message.getMessageId(), ignored -> new AtomicInteger())
                .incrementAndGet();
        workerOne = worker(publisher);
        workerTwo = worker(publisher);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = callers.submit(() -> awaitAndPublish(start, workerOne));
            Future<?> second = callers.submit(() -> awaitAndPublish(start, workerTwo));
            start.countDown();
            first.get();
            second.get();
        } finally {
            callers.shutdownNow();
        }

        assertThat(deliveries).hasSize(40);
        assertThat(deliveries.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_message_outbox WHERE status = 2", Integer.class)).isEqualTo(40);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_message_outbox WHERE status = 1", Integer.class)).isZero();
    }

    @Test
    void olderCommittedMessageCannotRegressConversationMetadata() {
        jdbcTemplate.update("""
                INSERT INTO chat_conversation
                  (id, next_message_seq, last_message_id, last_message_seq, last_message_time, updated_at)
                VALUES (7, 10, 100, 10, 1000, CURRENT_TIMESTAMP(3))
                """);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(ChatConversationMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);

        try (SqlSession newer = factory.openSession(true); SqlSession older = factory.openSession(true)) {
            newer.getMapper(ChatConversationMapper.class).advanceLatestMessage(
                    7L, 120L, 12L, 1200L, LocalDateTime.of(2026, 1, 1, 0, 0, 12));
            older.getMapper(ChatConversationMapper.class).advanceLatestMessage(
                    7L, 110L, 11L, 1100L, LocalDateTime.of(2026, 1, 1, 0, 0, 11));
        }

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT next_message_seq, last_message_seq, last_message_id FROM chat_conversation WHERE id = 7");
        assertThat(((Number) row.get("next_message_seq")).longValue()).isEqualTo(12L);
        assertThat(((Number) row.get("last_message_seq")).longValue()).isEqualTo(12L);
        assertThat(((Number) row.get("last_message_id")).longValue()).isEqualTo(120L);
    }

    @Test
    void expiredClaimIsRecoveredByAnotherWorker() throws Exception {
        MessageVO message = new MessageVO();
        message.setMessageId("91");
        message.setSessionId(7L);
        jdbcTemplate.update("""
                INSERT INTO chat_message_outbox
                  (id, message_id, recipient_id, event_type, payload, status, claim_token, updated_at)
                VALUES (91, 91, 20, 'recipient_push', ?, 1, 'dead-worker',
                        CURRENT_TIMESTAMP(3) - INTERVAL 5 MINUTE)
                """, new ObjectMapper().writeValueAsString(message));
        AtomicInteger deliveries = new AtomicInteger();
        workerOne = worker((recipientId, delivered) -> deliveries.incrementAndGet());

        workerOne.publishPending();

        assertThat(deliveries.get()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM chat_message_outbox WHERE id = 91", Integer.class)).isEqualTo(2);
    }

    @Test
    void shardOnlyUnreadRefreshReadsTheConversationShard() throws Exception {
        jdbcTemplate.execute("""
                CREATE TABLE chat_message_s07 (
                    id BIGINT PRIMARY KEY, conversation_id BIGINT NOT NULL,
                    recipient_id BIGINT NOT NULL, visited INT NOT NULL
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.update("INSERT INTO chat_message_s07 VALUES (1, 7, 20, 10)");
        MessageVO message = new MessageVO();
        message.setMessageId("92");
        message.setSessionId(7L);
        jdbcTemplate.update("""
                INSERT INTO chat_message_outbox
                  (id, message_id, recipient_id, event_type, payload)
                VALUES (92, 92, 20, 'unread_refresh', ?)
                """, new ObjectMapper().writeValueAsString(message));
        AtomicLong observedUnread = new AtomicLong(-1);
        ChatUnreadService unread = new ChatUnreadService(null) {
            @Override
            public void setCount(Long userId, Long conversationId, long count) {
                observedUnread.set(count);
            }
        };
        workerOne = worker((recipientId, delivered) -> { }, unread);
        ReflectionTestUtils.setField(workerOne, "storageMode", ChatMessageStorageMode.SHARD_ONLY);

        workerOne.publishPending();

        assertThat(observedUnread.get()).isEqualTo(1L);
    }

    private ChatMessageOutboxService worker(ChatMessagePublisher publisher) {
        return worker(publisher, null);
    }

    private ChatMessageOutboxService worker(ChatMessagePublisher publisher, ChatUnreadService unreadService) {
        ChatMessageOutboxService worker = new ChatMessageOutboxService(
                jdbcTemplate, new ObjectMapper(), publisher, noOpRecentCache(),
                unreadService, null,
                new ChatMessageShardRouter(32));
        ReflectionTestUtils.setField(worker, "batchSize", 100);
        ReflectionTestUtils.setField(worker, "workerThreads", 4);
        ReflectionTestUtils.setField(worker, "processingTimeoutMillis", 60_000L);
        ReflectionTestUtils.setField(worker, "maxRetries", 3);
        ReflectionTestUtils.invokeMethod(worker, "init");
        return worker;
    }

    private RecentMessageCache noOpRecentCache() {
        return new RecentMessageCache() {
            @Override
            public List<MessageVO> getRecent(Long conversationId, int count,
                                             Supplier<List<MessageVO>> loader) {
                return loader.get();
            }

            @Override
            public void append(MessageVO message) {
                // The integration seam is durable claiming, not Redis caching.
            }
        };
    }

    private void awaitAndPublish(CountDownLatch start, ChatMessageOutboxService worker) {
        try {
            start.await();
            worker.publishPending();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private void shutdown(ChatMessageOutboxService worker) {
        if (worker != null) {
            ReflectionTestUtils.invokeMethod(worker, "shutdown");
        }
    }
}
