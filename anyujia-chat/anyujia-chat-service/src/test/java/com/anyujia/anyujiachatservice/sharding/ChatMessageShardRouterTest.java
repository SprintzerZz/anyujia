package com.anyujia.anyujiachatservice.sharding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChatMessageShardRouterTest {
    @Test
    void routesConversationToStableVirtualBucketAndTable() {
        ChatMessageShardRouter router = new ChatMessageShardRouter(32);

        assertThat(router.virtualBucket(123L)).isBetween(0, 1023);
        assertThat(router.physicalShard(123L)).isBetween(0, 31);
        assertThat(router.tableName(123L)).matches("chat_message_s\\d{2}");
        assertThat(router.tableName(123L)).isEqualTo(router.tableName(123L));
    }

    @Test
    void rejectsShardCountThatCannotMapVirtualBucketsEvenly() {
        assertThatThrownBy(() -> new ChatMessageShardRouter(30))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
