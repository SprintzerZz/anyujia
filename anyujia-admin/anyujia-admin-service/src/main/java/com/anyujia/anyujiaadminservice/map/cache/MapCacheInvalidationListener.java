package com.anyujia.anyujiaadminservice.map.cache;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 地图缓存失效消息监听器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "map.cache", name = "invalidate-broadcast-enabled", havingValue = "true")
public class MapCacheInvalidationListener {

    /**
     * Caffeine 本地缓存。
     */
    private final Cache<String, Object> caffeineCache;

    /**
     * 接收缓存失效广播，只清理当前实例的本地缓存。
     *
     * @param message 缓存失效消息
     */
    @RabbitListener(queues = "#{mapCacheInvalidateQueue.name}")
    public void onMessage(MapCacheInvalidationMessage message) {
        if (message == null || message.getKeys() == null || message.getKeys().isEmpty()) {
            return;
        }

        String eventCacheKey = getEventCacheKey(message.getEventId());
        if (eventCacheKey != null && caffeineCache.getIfPresent(eventCacheKey) != null) {
            log.info("Ignore duplicate map cache invalidation message, eventId={}", message.getEventId());
            return;
        }

        for (String key : message.getKeys()) {
            caffeineCache.invalidate(key);
            log.info("Invalidate local map cache by broadcast, key={}, eventId={}, version={}",
                    key, message.getEventId(), message.getVersion());
        }

        if (eventCacheKey != null) {
            caffeineCache.put(eventCacheKey, Boolean.TRUE);
        }
    }

    /**
     * 生成 MQ 事件幂等缓存 key。
     *
     * @param eventId 事件 ID
     * @return 幂等缓存 key
     */
    private String getEventCacheKey(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            return null;
        }
        return "cache:map:event:" + eventId;
    }
}
