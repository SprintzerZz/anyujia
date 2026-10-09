package com.anyujia.anyujiachatservice.service.ratelimit;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "chat.rate-limit.conversation", name = "type", havingValue = "local")
public class LocalConversationRateLimiter implements ConversationRateLimiter {
    private final ConversationRateLimitProperties properties;
    private final Map<Long, Window> windows = new ConcurrentHashMap<>();

    public LocalConversationRateLimiter(ConversationRateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean tryAcquire(Long conversationId) {
        if (!properties.isEnabled() || conversationId == null
                || properties.getMaxMessagesPerSecond() <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(conversationId, ignored -> new Window(now));
        synchronized (window) {
            if (now - window.windowStartMillis >= TimeUnit.SECONDS.toMillis(1)) {
                window.windowStartMillis = now;
                window.count = 0;
            }
            window.lastSeenMillis = now;
            if (window.count >= properties.getMaxMessagesPerSecond()) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    @Scheduled(fixedDelayString = "${chat.rate-limit.conversation.cleanup-millis:60000}")
    public void cleanupIdleWindows() {
        long expiredBefore = System.currentTimeMillis()
                - TimeUnit.SECONDS.toMillis(properties.getIdleExpireSeconds());
        Iterator<Map.Entry<Long, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().lastSeenMillis < expiredBefore) {
                iterator.remove();
            }
        }
    }

    private static final class Window {
        private long windowStartMillis;
        private long lastSeenMillis;
        private int count;

        private Window(long now) {
            this.windowStartMillis = now;
            this.lastSeenMillis = now;
        }
    }
}
