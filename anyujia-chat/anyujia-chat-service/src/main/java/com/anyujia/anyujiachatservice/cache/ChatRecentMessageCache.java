package com.anyujia.anyujiachatservice.cache;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiacommonredis.service.RedisService;
import com.anyujia.anyujiacommonredis.service.RedissonLockService;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRecentMessageCache implements RecentMessageCache {
    private static final String MESSAGE_KEY_PREFIX = "chat:recent:messages:";
    private static final String LOADED_KEY_PREFIX = "chat:recent:loaded:";
    private static final String LOCK_KEY_PREFIX = "chat:recent:lock:";
    private static final String CACHE_EMPTY = "EMPTY";
    private static final String CACHE_POPULATED = "POPULATED";
    private static final int MAX_CACHED_MESSAGES = 200;
    private static final long BASE_TTL_SECONDS = TimeUnit.HOURS.toSeconds(24);
    private static final long TTL_JITTER_SECONDS = TimeUnit.HOURS.toSeconds(6);

    private final RedisService redisService;
    private final RedissonLockService lockService;

    @Override
    public List<MessageVO> getRecent(Long conversationId, int count, Supplier<List<MessageVO>> loader) {
        try {
            if (isLoaded(conversationId)) {
                return readTail(conversationId, count);
            }
            RLock lock = lockService.tryAcquire(lockKey(conversationId), 1, 5, TimeUnit.SECONDS);
            if (lock == null) {
                return loader.get();
            }
            try {
                if (isLoaded(conversationId)) {
                    return readTail(conversationId, count);
                }
                List<MessageVO> messages = loader.get();
                replace(conversationId, messages);
                return tail(messages, count);
            } finally {
                lockService.releaseLock(lock);
            }
        } catch (RuntimeException ex) {
            log.warn("Recent chat cache unavailable, falling back to database, conversationId={}",
                    conversationId, ex);
            return loader.get();
        }
    }

    @Override
    public void append(MessageVO message) {
        if (message == null || message.getSessionId() == null || message.getConversationSeq() == null) {
            return;
        }
        try {
            Long conversationId = message.getSessionId();
            if (!isLoaded(conversationId)) {
                return;
            }
            String key = messageKey(conversationId);
            redisService.addMemberZSet(key, message, Double.parseDouble(message.getConversationSeq()));
            redisService.removeZSetByRank(key, 0, -(MAX_CACHED_MESSAGES + 1L));
            long ttl = ttlSeconds();
            redisService.expire(key, ttl);
            redisService.setCacheObject(
                    loadedKey(conversationId), CACHE_POPULATED, ttl, TimeUnit.SECONDS);
        } catch (RuntimeException ex) {
            log.warn("Failed to append recent chat cache, conversationId={}, messageId={}",
                    message.getSessionId(), message.getMessageId(), ex);
        }
    }

    private void replace(Long conversationId, List<MessageVO> messages) {
        String key = messageKey(conversationId);
        redisService.deleteObject(key);
        tail(messages, MAX_CACHED_MESSAGES).forEach(message -> redisService.addMemberZSet(
                key, message, Double.parseDouble(message.getConversationSeq())));
        long ttl = ttlSeconds();
        if (!messages.isEmpty()) {
            redisService.expire(key, ttl);
        }
        redisService.setCacheObject(loadedKey(conversationId),
                messages.isEmpty() ? CACHE_EMPTY : CACHE_POPULATED, ttl, TimeUnit.SECONDS);
    }

    private List<MessageVO> readTail(Long conversationId, int count) {
        LinkedHashSet<MessageVO> cached = new LinkedHashSet<>(redisService.getCacheZSetByRange(
                messageKey(conversationId), -count, -1,
                new TypeReference<LinkedHashSet<MessageVO>>() { }));
        return new ArrayList<>(cached);
    }

    private boolean isLoaded(Long conversationId) {
        String state = redisService.getCacheObject(loadedKey(conversationId), String.class);
        if (CACHE_EMPTY.equals(state)) {
            return true;
        }
        return CACHE_POPULATED.equals(state)
                && Boolean.TRUE.equals(redisService.hasKey(messageKey(conversationId)));
    }

    private List<MessageVO> tail(List<MessageVO> messages, int count) {
        int from = Math.max(0, messages.size() - count);
        return new ArrayList<>(messages.subList(from, messages.size()));
    }

    private long ttlSeconds() {
        return BASE_TTL_SECONDS + ThreadLocalRandom.current().nextLong(TTL_JITTER_SECONDS + 1);
    }

    private String messageKey(Long conversationId) {
        return MESSAGE_KEY_PREFIX + conversationId;
    }

    private String loadedKey(Long conversationId) {
        return LOADED_KEY_PREFIX + conversationId;
    }

    private String lockKey(Long conversationId) {
        return LOCK_KEY_PREFIX + conversationId;
    }
}
