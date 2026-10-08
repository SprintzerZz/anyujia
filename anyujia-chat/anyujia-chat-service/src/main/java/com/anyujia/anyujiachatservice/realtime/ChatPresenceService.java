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
        String key = PREFIX + userId;
        redisService.setCacheMapValue(key, nodeId, System.currentTimeMillis());
        redisService.expire(key, TTL_SECONDS * 2, TimeUnit.SECONDS);
    }

    public void heartbeat(Long userId, String nodeId) {
        online(userId, nodeId);
    }

    public void offline(Long userId, String nodeId) {
        redisService.deleteCacheMapValue(PREFIX + userId, nodeId);
    }

    @Override
    public Set<String> nodes(Long userId) {
        String key = PREFIX + userId;
        Map<String, Long> heartbeats = redisService.getCacheMap(key, new TypeReference<Map<String, Long>>() { });
        long expiredBefore = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(TTL_SECONDS);
        Set<String> activeNodes = new HashSet<>();
        heartbeats.forEach((nodeId, timestamp) -> {
            if (timestamp != null && timestamp >= expiredBefore) {
                activeNodes.add(nodeId);
            } else {
                redisService.deleteCacheMapValue(key, nodeId);
            }
        });
        return activeNodes;
    }
}
