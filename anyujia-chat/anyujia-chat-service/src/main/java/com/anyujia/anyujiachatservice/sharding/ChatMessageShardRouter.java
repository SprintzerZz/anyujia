package com.anyujia.anyujiachatservice.sharding;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stable routing for message storage. Virtual buckets make later resharding possible.
 *
 * <p>Three-level mapping:
 * <pre>
 *   virtual_bucket = conversation_id % 1024
 *   physical_shard = virtual_bucket % physicalShardCount
 *   physical_mysql = physical_shard / shardsPerMysql
 * </pre>
 * A conversation's messages all share one {@code conversation_id}, so they always land on the
 * same physical shard and the same MySQL instance (one-conversation-one-instance), avoiding
 * cross-instance joins and distributed writes for a single conversation.
 */
@Component
public class ChatMessageShardRouter {
    private static final int VIRTUAL_BUCKETS = 1024;
    private final int physicalShardCount;
    private final int shardsPerMysql;

    @Autowired
    public ChatMessageShardRouter(
            @Value("${chat.message-sharding.physical-shard-count:32}") int physicalShardCount,
            @Value("${chat.message-sharding.shards-per-mysql:8}") int shardsPerMysql) {
        if (physicalShardCount < 1 || physicalShardCount > VIRTUAL_BUCKETS
                || (VIRTUAL_BUCKETS % physicalShardCount) != 0) {
            throw new IllegalArgumentException("physical shard count must divide 1024 and be between 1 and 1024");
        }
        if (shardsPerMysql < 1 || shardsPerMysql > physicalShardCount
                || (physicalShardCount % shardsPerMysql) != 0) {
            throw new IllegalArgumentException("shards per MySQL must divide the physical shard count");
        }
        this.physicalShardCount = physicalShardCount;
        this.shardsPerMysql = shardsPerMysql;
    }

    /** Single-database convenience constructor (all shards on one MySQL instance). */
    public ChatMessageShardRouter(int physicalShardCount) {
        this(physicalShardCount, physicalShardCount);
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
        return tableNameForShard(physicalShard(conversationId));
    }

    public String tableNameForShard(int physicalShard) {
        return String.format("chat_message_s%02d", physicalShard);
    }

    public int physicalMysqlIndex(Long conversationId) {
        return physicalMysqlIndexForShard(physicalShard(conversationId));
    }

    public int physicalMysqlIndexForShard(int physicalShard) {
        return physicalShard / shardsPerMysql;
    }

    public int physicalShardCount() {
        return physicalShardCount;
    }

    public int shardsPerMysql() {
        return shardsPerMysql;
    }

    public int mysqlInstanceCount() {
        return physicalShardCount / shardsPerMysql;
    }
}
