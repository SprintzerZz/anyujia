package com.anyujia.anyujiachatservice.service.sequence;

import com.anyujia.anyujiachatservice.mapper.ChatConversationMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

/**
 * Per-conversation sequence allocator backed by a Redis atomic counter.
 *
 * <p>Replaces the previous {@code SELECT ... FOR UPDATE} row lock on
 * {@code chat_conversation.next_message_seq}, which serialized every write to the same
 * conversation. The counter is lazily seeded from the persisted {@code next_message_seq}
 * on first use; {@link #next} is safe to call concurrently.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisConversationSequenceAllocator {
    private static final String SEQ_KEY_PREFIX = "chat:seq:";

    /** Returns -1 instead of creating the key, so the caller can seed it from the database. */
    private static final String ALLOCATE_LUA = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end
            return redis.call('INCR', KEYS[1])
            """;

    /** Seeds from the persisted counter only when nobody else has, then allocates. */
    private static final String SEED_AND_ALLOCATE_LUA = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                redis.call('SET', KEYS[1], ARGV[1])
            end
            return redis.call('INCR', KEYS[1])
            """;

    private final RedissonClient redissonClient;
    private final ChatConversationMapper conversationMapper;

    public Long next(Long conversationId) {
        List<Object> keys = List.of(SEQ_KEY_PREFIX + conversationId);
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        // Warm path is a single round trip; the old EXISTS + INCRBY pair doubled Redis latency on
        // a path that runs once per message.
        Long allocated = script.eval(RScript.Mode.READ_WRITE, ALLOCATE_LUA,
                RScript.ReturnType.INTEGER, keys);
        if (allocated != null && allocated >= 0) {
            return allocated;
        }
        Long dbSeq = conversationMapper.selectNextMessageSeq(conversationId);
        long seed = dbSeq == null ? 0L : dbSeq;
        return script.eval(RScript.Mode.READ_WRITE, SEED_AND_ALLOCATE_LUA,
                RScript.ReturnType.INTEGER, keys, String.valueOf(seed));
    }
}
