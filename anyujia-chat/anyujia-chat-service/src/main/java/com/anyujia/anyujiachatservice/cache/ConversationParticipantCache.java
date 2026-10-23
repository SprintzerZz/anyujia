package com.anyujia.anyujiachatservice.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Process-local cache of a conversation's counterparty (the member that is not the sender).
 *
 * <p>The message send path used to read {@code chat_conversation} plus
 * {@code chat_conversation_member} on every message just to resolve the recipient. Those two rows
 * are effectively immutable after the conversation is created (membership is only written at
 * creation time), so they are cached here. Membership itself is deliberately <em>not</em> cached:
 * {@code requireMember} still hits the database on every send, so a removed member loses access
 * immediately. A stale entry can therefore only pick the wrong recipient inside a group
 * conversation, and only until the entry expires.</p>
 */
@Component
public class ConversationParticipantCache {
    private static final int MAX_ENTRIES = 100_000;

    private final ConcurrentHashMap<ParticipantKey, Entry> cache = new ConcurrentHashMap<>();
    private final AtomicInteger size = new AtomicInteger();

    @Value("${chat.conversation.participant-cache-ttl-millis:60000}")
    private long ttlMillis = 60_000L;

    public Long get(Long conversationId, Long userId) {
        if (conversationId == null || userId == null) {
            return null;
        }
        ParticipantKey key = new ParticipantKey(conversationId, userId);
        Entry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt() <= System.currentTimeMillis()) {
            if (cache.remove(key, entry)) {
                size.decrementAndGet();
            }
            return null;
        }
        return entry.counterpartyId();
    }

    public void put(Long conversationId, Long userId, Long counterpartyId) {
        if (conversationId == null || userId == null || counterpartyId == null) {
            return;
        }
        // Cheap bound: the entry count is a hard ceiling rather than a fine-grained eviction
        // policy; the conversation keyspace is far smaller than this in practice.
        if (size.get() >= MAX_ENTRIES) {
            cache.clear();
            size.set(0);
        }
        Entry previous = cache.put(new ParticipantKey(conversationId, userId),
                new Entry(counterpartyId, System.currentTimeMillis() + Math.max(1_000L, ttlMillis)));
        if (previous == null) {
            size.incrementAndGet();
        }
    }

    /** Drops every cached recipient for a conversation; call when membership changes. */
    public void invalidate(Long conversationId) {
        if (conversationId == null) {
            return;
        }
        int removed = 0;
        for (ParticipantKey key : cache.keySet()) {
            if (key.conversationId().equals(conversationId) && cache.remove(key) != null) {
                removed++;
            }
        }
        if (removed > 0) {
            size.addAndGet(-removed);
        }
    }

    private record ParticipantKey(Long conversationId, Long userId) {
    }

    private record Entry(Long counterpartyId, long expiresAt) {
    }
}
