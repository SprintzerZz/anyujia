package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujialegalservice.config.LegalMemoryProperties;
import com.anyujia.anyujialegalservice.domain.model.ShortTermChatMemory;
import com.anyujia.anyujiacommonredis.service.RedisService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalShortTermMemoryService {
    private static final String CHAT_KEY_PREFIX = "legal:memory:chat:";

    private final LegalMemoryProperties properties;
    private final ObjectProvider<RedisService> redisServiceProvider;
    private volatile boolean redisUnavailable;

    public Optional<List<MessageVO>> getChatWindow(Long tenantId, Long conversationId, int limit) {
        if (!properties.isRedisEnabled() || redisUnavailable) {
            return Optional.empty();
        }
        RedisService redisService = redisService();
        if (redisService == null) {
            return Optional.empty();
        }
        try {
            ShortTermChatMemory memory = redisService.getCacheObject(
                    chatKey(tenantId, conversationId, limit), ShortTermChatMemory.class);
            if (memory == null || memory.getMessages() == null) {
                return Optional.empty();
            }
            return Optional.of(memory.getMessages());
        } catch (Exception ex) {
            redisUnavailable = true;
            log.warn("Legal short-term memory read disabled after Redis failure: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public void putChatWindow(Long tenantId, Long conversationId, int limit, List<MessageVO> messages) {
        if (!properties.isRedisEnabled() || redisUnavailable || messages == null) {
            return;
        }
        RedisService redisService = redisService();
        if (redisService == null) {
            return;
        }
        try {
            ShortTermChatMemory memory = new ShortTermChatMemory();
            memory.setTenantId(tenantId);
            memory.setConversationId(conversationId);
            memory.setLimit(limit);
            memory.setMessages(trim(messages, limit));
            memory.setMessageCount(memory.getMessages().size());
            memory.setCachedAt(LocalDateTime.now());
            redisService.setCacheObject(chatKey(tenantId, conversationId, limit), memory,
                    properties.getShortTermTtlSeconds(), TimeUnit.SECONDS);
        } catch (Exception ex) {
            redisUnavailable = true;
            log.warn("Legal short-term memory write disabled after Redis failure: {}", ex.getMessage());
        }
    }

    public boolean evictConversation(Long tenantId, Long conversationId) {
        if (!properties.isRedisEnabled() || redisUnavailable) {
            return false;
        }
        RedisService redisService = redisService();
        if (redisService == null) {
            return false;
        }
        try {
            return redisService.deleteObject(redisService.keys(CHAT_KEY_PREFIX + tenantId + ":" + conversationId + ":*"));
        } catch (Exception ex) {
            redisUnavailable = true;
            log.warn("Legal short-term memory eviction disabled after Redis failure: {}", ex.getMessage());
            return false;
        }
    }

    public int normalizeLimit(int requestedLimit) {
        return Math.max(1, Math.min(requestedLimit, properties.getChatWindowLimit()));
    }

    private RedisService redisService() {
        try {
            return redisServiceProvider.getIfAvailable();
        } catch (Exception ex) {
            redisUnavailable = true;
            log.warn("Legal short-term memory unavailable: {}", ex.getMessage());
            return null;
        }
    }

    private List<MessageVO> trim(List<MessageVO> messages, int limit) {
        if (messages.size() <= limit) {
            return List.copyOf(messages);
        }
        return List.copyOf(messages.subList(0, limit));
    }

    private String chatKey(Long tenantId, Long conversationId, int limit) {
        return CHAT_KEY_PREFIX + tenantId + ":" + conversationId + ":" + limit;
    }
}
