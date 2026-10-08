package com.anyujia.anyujiachatservice.realtime;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatIdleConnectionReaper {
    private final ChatConnectionRegistry registry;

    @Value("${chat.websocket.idle-timeout-seconds:150}")
    private long idleTimeoutSeconds;

    @Scheduled(fixedDelayString = "${chat.websocket.idle-scan-millis:30000}")
    public void closeIdleConnections() {
        long idleBefore = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(idleTimeoutSeconds);
        registry.closeIdleSessions(idleBefore);
    }
}
