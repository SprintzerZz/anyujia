package com.anyujia.anyujiachatservice.sharding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stable routing for message storage. Virtual buckets make later resharding possible. */
@Component
public class ChatMessageShardRouter {
    private static final int VIRTUAL_BUCKETS = 1024;
    private final int physicalShardCount;

    public ChatMessageShardRouter(
            @Value("${chat.message-sharding.physical-shard-count:32}") int physicalShardCount) {
        if (physicalShardCount < 1 || physicalShardCount > VIRTUAL_BUCKETS
                || (VIRTUAL_BUCKETS % physicalShardCount) != 0) {
            throw new IllegalArgumentException("physical shard count must divide 1024 and be between 1 and 1024");
        }
        this.physicalShardCount = physicalShardCount;
    }

    public int virtualBucket(Long conversationId) {
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId is required");
        }
        // Keep the routing formula SQL-compatible for migration and backfill jobs.
        return Math.floorMod(conversationId, VIRTUAL_BUCKETS);
    }

    public int physicalShard(Long conversationId) {
        return virtualBucket(conversationId) % physicalShardCount;
    }

    public String tableName(Long conversationId) {
        return String.format("chat_message_s%02d", physicalShard(conversationId));
    }

    public int physicalShardCount() {
        return physicalShardCount;
    }
}
