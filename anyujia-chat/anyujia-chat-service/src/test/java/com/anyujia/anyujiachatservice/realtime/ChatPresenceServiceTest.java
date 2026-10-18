package com.anyujia.anyujiachatservice.realtime;

import static org.mockito.Mockito.verify;
import com.anyujia.anyujiacommonredis.service.RedisService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ChatPresenceServiceTest {
    @Test
    void heartbeatAndOfflineUseConnectionLeaseGuards() {
        RedisService redisService = org.mockito.Mockito.mock(RedisService.class);
        ChatPresenceService service = new ChatPresenceService(redisService);

        service.heartbeat(7L, "node-1", "connection-1");
        service.offline(7L, "node-1", "connection-1");

        verify(redisService).setCacheMapValueIfValueStartsWith(
                org.mockito.ArgumentMatchers.eq("chat:presence:user:7"),
                org.mockito.ArgumentMatchers.eq("node-1"),
                org.mockito.ArgumentMatchers.eq("connection-1|"),
                org.mockito.ArgumentMatchers.startsWith("connection-1|"));
        verify(redisService).deleteCacheMapValueIfValueStartsWith(
                "chat:presence:user:7", "node-1", "connection-1|");
        verify(redisService).expire("chat:presence:user:7", 360L, TimeUnit.SECONDS);
    }
}
