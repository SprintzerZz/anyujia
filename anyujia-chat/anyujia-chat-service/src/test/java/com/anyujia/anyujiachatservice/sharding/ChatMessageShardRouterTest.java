package com.anyujia.anyujiachatservice.sharding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChatMessageShardRouterTest {
    @Test
    void routesConversationToStableVirtualBucketAndTable() {
        ChatMessageShardRouter router = new ChatMessageShardRouter(32, 8);

        assertThat(router.virtualBucket(123L)).isBetween(0, 1023);
        assertThat(router.physicalShard(123L)).isBetween(0, 31);
        assertThat(router.tableName(123L)).matches("chat_message_s\\d{2}");
        assertThat(router.tableName(123L)).isEqualTo(router.tableName(123L));
    }

    @Test
    void mapsEveryPhysicalShardToTheExpectedMysqlInstanceAndTable() {
        ChatMessageShardRouter router = new ChatMessageShardRouter(32, 8);

        assertThat(router.mysqlInstanceCount()).isEqualTo(4);
        for (int shard = 0; shard < 32; shard++) {
            assertThat(router.tableNameForShard(shard)).isEqualTo(String.format("chat_message_s%02d", shard));
            assertThat(router.physicalMysqlIndexForShard(shard)).isEqualTo(shard / 8);
        }
        assertThat(router.physicalMysqlIndexForShard(0)).isEqualTo(0);
        assertThat(router.physicalMysqlIndexForShard(7)).isEqualTo(0);
        assertThat(router.physicalMysqlIndexForShard(8)).isEqualTo(1);
        assertThat(router.physicalMysqlIndexForShard(31)).isEqualTo(3);
    }

    @Test
    void conversationRoutesConsistentlyToSameInstanceAndTable() {
        ChatMessageShardRouter router = new ChatMessageShardRouter(32, 8);

        for (long conversationId : new long[]{1L, 7L, 900000000000100001L, 123L}) {
            int shard = router.physicalShard(conversationId);
            assertThat(router.physicalMysqlIndex(conversationId)).isEqualTo(shard / 8);
            assertThat(router.tableName(conversationId)).isEqualTo(router.tableNameForShard(shard));
        }
    }

    @Test
    void singleDatabaseRouterFallsBackToSingleInstance() {
        ChatMessageShardRouter router = new ChatMessageShardRouter(32);

        assertThat(router.mysqlInstanceCount()).isEqualTo(1);
        assertThat(router.physicalMysqlIndexForShard(31)).isEqualTo(0);
    }

    @Test
    void rejectsShardCountThatCannotMapVirtualBucketsEvenly() {
        assertThatThrownBy(() -> new ChatMessageShardRouter(30, 8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsShardsPerMysqlThatDoesNotDivideShardCount() {
        assertThatThrownBy(() -> new ChatMessageShardRouter(32, 7))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
