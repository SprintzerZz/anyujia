package com.anyujia.anyujiachatservice.sharding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ChatMessageShardStoreTest {

    private final ChatMessageShardRouter router = new ChatMessageShardRouter(32, 8);

    private ChatMessageShardStore storeWith(List<JdbcTemplate> templates) {
        return new ChatMessageShardStore(templates.toArray(new JdbcTemplate[0]), router);
    }

    @Test
    void routesEveryConversationToTheExpectedTemplateAndTable() {
        JdbcTemplate t0 = mock(JdbcTemplate.class);
        JdbcTemplate t1 = mock(JdbcTemplate.class);
        JdbcTemplate t2 = mock(JdbcTemplate.class);
        JdbcTemplate t3 = mock(JdbcTemplate.class);
        ChatMessageShardStore store = storeWith(List.of(t0, t1, t2, t3));
        List<JdbcTemplate> templates = List.of(t0, t1, t2, t3);

        // A sample of conversation ids spread across the 4 physical instances.
        long[] conversationIds = {1L, 8L, 16L, 31L, 123L, 900000000000100001L};
        for (long conversationId : conversationIds) {
            int expectedIndex = router.physicalMysqlIndex(conversationId);
            assertThat(store.templateFor(conversationId)).isSameAs(templates.get(expectedIndex));
            assertThat(store.tableFor(conversationId)).isEqualTo(router.tableName(conversationId));
        }
    }

    @Test
    void coversAllFourInstancesForTheSampledConversations() {
        JdbcTemplate t0 = mock(JdbcTemplate.class);
        JdbcTemplate t1 = mock(JdbcTemplate.class);
        JdbcTemplate t2 = mock(JdbcTemplate.class);
        JdbcTemplate t3 = mock(JdbcTemplate.class);
        ChatMessageShardStore store = storeWith(List.of(t0, t1, t2, t3));

        assertThat(store.instanceCount()).isEqualTo(4);
        // Bucket 0 -> shard 0 -> instance 0, bucket 8 -> shard 8 -> instance 1, etc.
        assertThat(store.templateFor(0L)).isSameAs(t0);
        assertThat(store.templateFor(8L)).isSameAs(t1);
        assertThat(store.templateFor(16L)).isSameAs(t2);
        assertThat(store.templateFor(24L)).isSameAs(t3);
    }
}
