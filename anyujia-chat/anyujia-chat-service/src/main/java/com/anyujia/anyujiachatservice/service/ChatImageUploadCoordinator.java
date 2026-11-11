package com.anyujia.anyujiachatservice.service;

import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatImageUploadCoordinator {
    private static final long PERMIT_LEASE_MILLIS = 300_000L;
    private static final String ACQUIRE_LUA = """
            local clock = redis.call('TIME')
            local now = clock[1] * 1000 + math.floor(clock[2] / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[1]) then return 0 end
            redis.call('ZADD', KEYS[1], now + tonumber(ARGV[2]), ARGV[3])
            redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[2]))
            return 1
            """;
    private static final String RELEASE_LUA = "return redis.call('ZREM', KEYS[1], ARGV[1])";

    private final RedissonClient redissonClient;

    @Value("${chat.image.global-max-concurrent-uploads:8}")
    private int globalMaxConcurrentUploads = 8;

    @Value("${chat.image.redis-prefix:chat:image}")
    private String redisPrefix = "chat:image";

    public <T> T withUploadLock(Long userId, Long conversationId, String uploadId, Supplier<T> action) {
        RLock lock = redissonClient.getLock(redisPrefix + ":upload:" + userId + ":" + conversationId + ":" + uploadId);
        try {
            // Redisson watchdog renews the lock while this node is alive.
            if (!lock.tryLock(0, TimeUnit.SECONDS)) throw busy();
            try {
                return action.get();
            } finally {
                if (lock.isHeldByCurrentThread()) lock.unlock();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw busy();
        }
    }

    public <T> T withGlobalPermit(Supplier<T> action) {
        String token = UUID.randomUUID().toString();
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        Long acquired = script.eval(RScript.Mode.READ_WRITE, ACQUIRE_LUA, RScript.ReturnType.INTEGER,
                List.of(redisPrefix + ":uploads:active"), String.valueOf(Math.max(1, globalMaxConcurrentUploads)),
                String.valueOf(PERMIT_LEASE_MILLIS), token);
        if (acquired == null || acquired != 1L) throw busy();
        try {
            return action.get();
        } finally {
            try {
                script.eval(RScript.Mode.READ_WRITE, RELEASE_LUA, RScript.ReturnType.INTEGER,
                        List.of(redisPrefix + ":uploads:active"), token);
            } catch (RuntimeException ex) {
                // The Redis lease will expire even if release cannot run.
                log.warn("Failed to release chat image upload permit", ex);
            }
        }
    }

    private ServiceException busy() {
        return new ServiceException("Too many image uploads; retry shortly", ResultCode.TOO_MANY_REQUESTS.getCode());
    }
}
