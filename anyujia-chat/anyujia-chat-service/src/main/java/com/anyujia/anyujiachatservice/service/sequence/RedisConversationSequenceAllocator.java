package com.anyujia.anyujiachatservice.service.sequence;

import com.anyujia.anyujiachatservice.mapper.ChatConversationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
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

    private final RedissonClient redissonClient;
    private final ChatConversationMapper conversationMapper;

    public Long next(Long conversationId) {
        RAtomicLong counter = redissonClient.getAtomicLong(SEQ_KEY_PREFIX + conversationId);
        if (!counter.isExists()) {
            Long dbSeq = conversationMapper.selectNextMessageSeq(conversationId);
            long seed = dbSeq == null ? 0L : dbSeq;
            // Only seed when the counter is still unset; a concurrent thread may win first.
            counter.compareAndSet(0L, seed);
        }
        return counter.incrementAndGet();
    }
}
