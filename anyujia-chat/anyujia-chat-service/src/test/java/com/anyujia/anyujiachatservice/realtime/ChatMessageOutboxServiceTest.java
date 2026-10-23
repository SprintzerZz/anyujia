package com.anyujia.anyujiachatservice.realtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiachatservice.cache.RecentMessageCache;
import com.anyujia.anyujiachatservice.cache.ChatConversationIndexCache;
import com.anyujia.anyujiachatservice.service.unread.ChatUnreadService;
import com.anyujia.anyujiachatservice.sharding.ChatMessageShardRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

class ChatMessageOutboxServiceTest {
    @Test
    void cleanupDeletesOnlyExpiredSentRowsInBoundedBatch() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChatMessageOutboxService service = new ChatMessageOutboxService(
                jdbcTemplate, new ObjectMapper(), mock(ChatMessagePublisher.class), mock(RecentMessageCache.class),
                mock(ChatUnreadService.class), mock(ChatConversationIndexCache.class),
                new ChatMessageShardRouter(32));
        ReflectionTestUtils.setField(service, "sentRetentionDays", 7);
        ReflectionTestUtils.setField(service, "cleanupBatchSize", 20_000);
        when(jdbcTemplate.update(anyString(), any(Timestamp.class), eq(10_000))).thenReturn(3);

        service.cleanupSent();

        verify(jdbcTemplate).update(anyString(), any(Timestamp.class), eq(10_000));
    }

    @Test
    void publishingUpdatesRecentCacheBeforePush() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChatMessagePublisher publisher = mock(ChatMessagePublisher.class);
        RecentMessageCache recentCache = mock(RecentMessageCache.class);
        ChatMessageOutboxService service = new ChatMessageOutboxService(
                jdbcTemplate, new ObjectMapper(), publisher, recentCache,
                mock(ChatUnreadService.class), mock(ChatConversationIndexCache.class),
                new ChatMessageShardRouter(32));
        ReflectionTestUtils.setField(service, "batchSize", 1);
        ReflectionTestUtils.setField(service, "workerThreads", 1);
        service.init();

        MessageVO message = new MessageVO();
        message.setMessageId("100");
        message.setSessionId(9L);
        String payload = new ObjectMapper().writeValueAsString(message);
        when(jdbcTemplate.update(anyString(), anyString(), any(Timestamp.class), eq(1))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<Object> rowMapper = (RowMapper<Object>) invocation.getArgument(1);
                    var resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(7L);
                    when(resultSet.getLong("message_id")).thenReturn(100L);
                    when(resultSet.getLong("recipient_id")).thenReturn(8L);
                    when(resultSet.getString("event_type"))
                            .thenReturn(ChatMessageOutboxService.EVENT_RECIPIENT_PUSH);
                    when(resultSet.getString("payload")).thenReturn(payload);
                    when(resultSet.getInt("retry_count")).thenReturn(0);
                    return List.of(rowMapper.mapRow(resultSet, 0));
                });
        try {
            service.publishPending();

            var order = inOrder(recentCache, publisher);
            order.verify(recentCache).append(message);
            order.verify(publisher).route(8L, message);
        } finally {
            service.shutdown();
        }
    }
}
