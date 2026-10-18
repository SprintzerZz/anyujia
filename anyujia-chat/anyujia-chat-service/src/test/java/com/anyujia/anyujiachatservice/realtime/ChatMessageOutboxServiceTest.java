package com.anyujia.anyujiachatservice.realtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class ChatMessageOutboxServiceTest {
    @Test
    void cleanupDeletesOnlyExpiredSentRowsInBoundedBatch() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChatMessageOutboxService service = new ChatMessageOutboxService(
                jdbcTemplate, new ObjectMapper(), mock(ChatMessagePublisher.class));
        ReflectionTestUtils.setField(service, "sentRetentionDays", 7);
        ReflectionTestUtils.setField(service, "cleanupBatchSize", 20_000);
        when(jdbcTemplate.update(anyString(), any(Timestamp.class), eq(10_000))).thenReturn(3);

        service.cleanupSent();

        verify(jdbcTemplate).update(anyString(), any(Timestamp.class), eq(10_000));
    }
}
