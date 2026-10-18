package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiacommonredis.service.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatPresenceService implements ChatRecipientNodeLocator {
    private static final String PREFIX = "chat:presence:user:";
    private static final long TTL_SECONDS = 180;
    private final RedisService redisService;

    public void online(Long userId, String nodeId) {
        online(userId, nodeId, nodeId);
    }

    public void online(Long userId, String nodeId, String connectionId) {
        String key = PREFIX + userId;
        redisService.setCacheMapValue(key, nodeId, connectionId + "|" + System.currentTimeMillis());
        redisService.expire(key, TTL_SECONDS * 2, TimeUnit.SECONDS);
    }

    public void heartbeat(Long userId, String nodeId) {
        heartbeat(userId, nodeId, nodeId);
    }

    public void heartbeat(Long userId, String nodeId, String connectionId) {
        String key = PREFIX + userId;
        redisService.setCacheMapValueIfValueStartsWith(
                key, nodeId, connectionId + "|", connectionId + "|" + System.currentTimeMillis());
        redisService.expire(key, TTL_SECONDS * 2, TimeUnit.SECONDS);
    }

    public void offline(Long userId, String nodeId) {
        redisService.deleteCacheMapValue(PREFIX + userId, nodeId);
    }

    public void offline(Long userId, String nodeId, String connectionId) {
        redisService.deleteCacheMapValueIfValueStartsWith(PREFIX + userId, nodeId, connectionId + "|");
    }

    @Override
    public Set<String> nodes(Long userId) {
        String key = PREFIX + userId;
        Map<String, Object> heartbeats = redisService.getCacheMap(key, new TypeReference<Map<String, Object>>() { });
        long expiredBefore = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(TTL_SECONDS);
        Set<String> activeNodes = new HashSet<>();
        heartbeats.forEach((nodeId, value) -> {
            long timestamp = timestamp(value);
            if (timestamp >= expiredBefore) {
                activeNodes.add(nodeId);
            } else {
                redisService.deleteCacheMapValue(key, nodeId);
            }
        });
        return activeNodes;
    }

    private long timestamp(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            int separator = string.lastIndexOf('|');
            try {
                return Long.parseLong(separator >= 0 ? string.substring(separator + 1) : string);
            } catch (NumberFormatException ignored) {
                return Long.MIN_VALUE;
            }
        }
        return Long.MIN_VALUE;
    }
}
