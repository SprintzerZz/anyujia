package com.anyujia.anyujiachatservice.service.unread;

import com.anyujia.anyujiacommonredis.service.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Redis-Hash backed per-conversation unread counter.
 *
 * <p>The write path increments the counter in Redis ({@code HINCRBY}) instead of issuing a
 * database write on every message. The database is reconciled asynchronously by
 * {@link ChatUnreadCalibrator}; dirty {@code userId:conversationId} entries are tracked in a
 * Redis set so calibration only touches changed conversations. All methods fail open on Redis
 * outage so message sending is never blocked by the counter.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatUnreadService {
    private static final String UNREAD_KEY_PREFIX = "chat:unread:";
    private static final String DIRTY_KEY = "chat:unread:dirty";

    private final RedisService redisService;

    public void increment(Long userId, Long conversationId) {
        try {
            redisService.incrementMapValue(unreadKey(userId), String.valueOf(conversationId), 1);
            redisService.addMember(DIRTY_KEY, dirtyMember(userId, conversationId));
        } catch (RuntimeException ex) {
            log.warn("Failed to increment unread counter, userId={}, conversationId={}",
                    userId, conversationId, ex);
        }
    }

    /** Writes an absolute value so replayed outbox events remain idempotent. */
    public void setCount(Long userId, Long conversationId, long count) {
        try {
            redisService.setCacheMapValue(unreadKey(userId), String.valueOf(conversationId), Math.max(0L, count));
            redisService.addMember(DIRTY_KEY, dirtyMember(userId, conversationId));
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to apply unread outbox event", ex);
        }
    }

    public void reset(Long userId, Long conversationId) {
        try {
            redisService.deleteCacheMapValue(unreadKey(userId), String.valueOf(conversationId));
            redisService.deleteMember(DIRTY_KEY, dirtyMember(userId, conversationId));
        } catch (RuntimeException ex) {
            log.warn("Failed to reset unread counter, userId={}, conversationId={}",
                    userId, conversationId, ex);
        }
    }

    public Long readCount(Long userId, Long conversationId) {
        try {
            Object value = redisService.getCacheMapValue(unreadKey(userId), String.valueOf(conversationId));
            return toLong(value);
        } catch (RuntimeException ex) {
            log.warn("Failed to read unread counter, userId={}, conversationId={}",
                    userId, conversationId, ex);
            return null;
        }
    }

    public Set<String> dirtyMembers() {
        try {
            Set<String> members = redisService.getCacheSet(DIRTY_KEY, new TypeReference<Set<String>>() { });
            return members == null ? Collections.emptySet() : members;
        } catch (RuntimeException ex) {
            log.warn("Failed to read unread dirty set", ex);
            return Collections.emptySet();
        }
    }

    public void markFlushed(String member) {
        try {
            redisService.deleteMember(DIRTY_KEY, member);
        } catch (RuntimeException ex) {
            log.warn("Failed to remove unread dirty member, member={}", member, ex);
        }
    }

    public Long[] parseDirtyMember(String member) {
        int idx = member.indexOf(':');
        if (idx <= 0 || idx == member.length() - 1) {
            return null;
        }
        try {
            return new Long[] { Long.valueOf(member.substring(0, idx)), Long.valueOf(member.substring(idx + 1)) };
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String unreadKey(Long userId) {
        return UNREAD_KEY_PREFIX + userId;
    }

    private String dirtyMember(Long userId, Long conversationId) {
        return userId + ":" + conversationId;
    }
}
