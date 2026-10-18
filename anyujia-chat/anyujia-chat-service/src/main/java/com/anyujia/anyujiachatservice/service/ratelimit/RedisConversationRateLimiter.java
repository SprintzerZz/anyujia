package com.anyujia.anyujiachatservice.service.ratelimit;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Redis-backed per-conversation token-bucket rate limiter.
 *
 * <p>The token bucket is implemented as a single atomic Lua script so the read-modify-write of
 * the bucket state is race-free across all service instances. Tokens refill at
 * {@code maxMessagesPerSecond} per second and burst up to the same capacity. On Redis outage
 * the limiter fails open so message sending is never blocked by the limiter.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "chat.rate-limit.conversation", name = "type", havingValue = "redis", matchIfMissing = true)
public class RedisConversationRateLimiter implements ConversationRateLimiter {
    private static final String KEY_PREFIX = "chat:ratelimit:";

    private static final String TOKEN_BUCKET_LUA = """
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            local ttl = tonumber(ARGV[5])

            local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local tokens = tonumber(bucket[1])
            local ts = tonumber(bucket[2])

            if tokens == nil then
                tokens = capacity
                ts = now
            end

            local elapsed = (now - ts) / 1000.0
            tokens = math.min(capacity, tokens + elapsed * refill_rate)
            ts = now

            local allowed = 0
            if tokens >= requested then
                tokens = tokens - requested
                allowed = 1
            end

            redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', ts)
            redis.call('EXPIRE', KEYS[1], ttl)
            return allowed
            """;

    private final RedissonClient redissonClient;
    private final ConversationRateLimitProperties properties;

    @Override
    public boolean tryAcquire(Long conversationId) {
        if (!properties.isEnabled() || conversationId == null
                || properties.getMaxMessagesPerSecond() <= 0) {
            return true;
        }
        try {
            RScript script = redissonClient.getScript(StringCodec.INSTANCE);
            Long allowed = script.eval(
                    RScript.Mode.READ_WRITE,
                    TOKEN_BUCKET_LUA,
                    RScript.ReturnType.INTEGER,
                    List.of(KEY_PREFIX + conversationId),
                    String.valueOf(properties.getMaxMessagesPerSecond()),
                    String.valueOf(properties.getMaxMessagesPerSecond()),
                    String.valueOf(System.currentTimeMillis()),
                    "1",
                    String.valueOf(properties.getIdleExpireSeconds()));
            return allowed == null || allowed == 1L;
        } catch (RuntimeException ex) {
            log.warn("Redis rate limiter unavailable, allowing request, conversationId={}", conversationId, ex);
            return true;
        }
    }
}
