package com.anyujia.anyujiachatservice.cache;

import com.anyujia.anyujiacommonredis.service.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Redis hot cache for the user's recent conversation index. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatConversationIndexCache {
    private static final String LIST_KEY_PREFIX = "chat:conversation:list:";
    private static final String LOADED_KEY_PREFIX = "chat:conversation:list:loaded:";
    private static final String UPDATE_KEY_PREFIX = "chat:conversation:index:update:";
    private static final String DIRTY_KEY = "chat:conversation:index:dirty";
    private static final long TTL_SECONDS = TimeUnit.MINUTES.toSeconds(10);
    private static final int MAX_CACHED_CONVERSATIONS = 100;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    public List<com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation> recent(
            Long userId, Long cursorTime, Long cursorConversationId, int count,
            Supplier<List<com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation>> loader) {
        if (cursorTime != null || cursorConversationId != null) {
            return loader.get();
        }
        try {
            String loadedKey = loadedKey(userId);
            if (Boolean.TRUE.equals(redisService.hasKey(loadedKey))) {
                return trim(redisService.getCacheList(listKey(userId),
                        new TypeReference<List<com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation>>() { }), count);
            }
            List<com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation> loaded = loader.get();
            replace(userId, loaded);
            return trim(loaded, count);
        } catch (RuntimeException ex) {
            log.warn("Conversation list cache unavailable, userId={}", userId, ex);
            return loader.get();
        }
    }

    public List<Long> touchMessageForUsers(List<Long> userIds, Long conversationId,
                                           Long messageId, Long messageSeq, Long messageTime) {
        IndexUpdate update = new IndexUpdate(messageId, messageSeq, messageTime);
        List<Long> failedUsers = new ArrayList<>();
        for (Long userId : userIds) {
            try {
                redisService.setCacheMapValue(updateKey(userId), String.valueOf(conversationId), update);
                redisService.addMember(DIRTY_KEY, dirtyMember(userId, conversationId));
                updateCachedList(userId, conversationId, update);
            } catch (RuntimeException ex) {
                failedUsers.add(userId);
                log.warn("Failed to update conversation index cache, userId={}, conversationId={}",
                        userId, conversationId, ex);
            }
        }
        return failedUsers;
    }

    public Set<String> dirtyMembers() {
        return redisService.getCacheSet(DIRTY_KEY, new TypeReference<Set<String>>() { });
    }

    public IndexUpdate readUpdate(Long userId, Long conversationId) {
        Object value = redisService.getCacheMapValue(updateKey(userId), String.valueOf(conversationId));
        return value == null ? null : objectMapper.convertValue(value, IndexUpdate.class);
    }

    public void markFlushed(Long userId, Long conversationId) {
        redisService.deleteCacheMapValue(updateKey(userId), String.valueOf(conversationId));
        redisService.deleteMember(DIRTY_KEY, dirtyMember(userId, conversationId));
    }

    public boolean isSameUpdate(Long userId, Long conversationId, IndexUpdate expected) {
        IndexUpdate current = readUpdate(userId, conversationId);
        return current != null && current.messageSeq().equals(expected.messageSeq())
                && current.messageId().equals(expected.messageId());
    }

    public Long[] parseDirtyMember(String member) {
        int separator = member.indexOf(':');
        if (separator <= 0 || separator == member.length() - 1) {
            return null;
        }
        try {
            return new Long[] {
                    Long.valueOf(member.substring(0, separator)),
                    Long.valueOf(member.substring(separator + 1))
            };
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record IndexUpdate(Long messageId, Long messageSeq, Long messageTime) { }

    private void replace(Long userId, List<com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation> values) {
        List<com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation> cached = new ArrayList<>(values);
        cached.sort(order());
        if (cached.size() > MAX_CACHED_CONVERSATIONS) {
            cached = new ArrayList<>(cached.subList(0, MAX_CACHED_CONVERSATIONS));
        }
        redisService.deleteObject(listKey(userId));
        if (!cached.isEmpty()) {
            redisService.setCacheList(listKey(userId), cached);
            redisService.expire(listKey(userId), TTL_SECONDS);
        }
        redisService.setCacheObject(loadedKey(userId), Boolean.TRUE, TTL_SECONDS, TimeUnit.SECONDS);
    }

    private void updateCachedList(Long userId, Long conversationId, IndexUpdate update) {
        String loadedKey = loadedKey(userId);
        if (!Boolean.TRUE.equals(redisService.hasKey(loadedKey))) {
            return;
        }
        List<com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation> values =
                redisService.getCacheList(listKey(userId), new TypeReference<List<com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation>>() { });
        boolean found = false;
        for (com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation value : values) {
            if (conversationId.equals(value.getConversationId())) {
                value.setLastMessageId(update.messageId());
                value.setLastMessageSeq(update.messageSeq());
                value.setLastMessageTime(update.messageTime());
                found = true;
                break;
            }
        }
        if (!found) {
            redisService.deleteObject(loadedKey);
            redisService.deleteObject(listKey(userId));
            return;
        }
        values.sort(order());
        redisService.deleteObject(listKey(userId));
        redisService.setCacheList(listKey(userId), values.subList(0, Math.min(values.size(), MAX_CACHED_CONVERSATIONS)));
        redisService.expire(listKey(userId), TTL_SECONDS);
        redisService.expire(loadedKey, TTL_SECONDS);
    }

    private List<com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation> trim(
            List<com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation> values, int count) {
        return values.subList(0, Math.min(values.size(), count));
    }

    private Comparator<com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation> order() {
        return Comparator.comparing(com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation::getLastMessageTime,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation::getConversationId,
                        Comparator.reverseOrder());
    }

    private String listKey(Long userId) { return LIST_KEY_PREFIX + userId; }
    private String loadedKey(Long userId) { return LOADED_KEY_PREFIX + userId; }
    private String updateKey(Long userId) { return UPDATE_KEY_PREFIX + userId; }
    private String dirtyMember(Long userId, Long conversationId) { return userId + ":" + conversationId; }
}
