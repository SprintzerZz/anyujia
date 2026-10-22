package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiacommonredis.service.RedisService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Tracks the latest grounded-context fingerprint for a tenant conversation. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegalAgentContextVersionService {
    private static final String KEY_PREFIX = "legal:agent:context-version:";
    private static final long TTL_SECONDS = 1800L;

    private final ObjectProvider<RedisService> redisServiceProvider;
    private volatile boolean redisUnavailable;

    public ChangeStatus record(Long tenantId, Long conversationId, String grounding) {
        if (redisUnavailable || tenantId == null || conversationId == null) {
            return ChangeStatus.UNKNOWN;
        }
        RedisService redis = redisService();
        if (redis == null) {
            return ChangeStatus.UNKNOWN;
        }
        String current = sha256(grounding);
        String key = KEY_PREFIX + tenantId + ":" + conversationId;
        try {
            String previous = redis.getCacheObject(key, String.class);
            redis.setCacheObject(key, current, TTL_SECONDS, TimeUnit.SECONDS);
            if (previous == null || previous.isBlank()) {
                return ChangeStatus.FIRST;
            }
            return previous.equals(current) ? ChangeStatus.UNCHANGED : ChangeStatus.CHANGED;
        } catch (Exception ex) {
            redisUnavailable = true;
            log.warn("Legal agent context version disabled after Redis failure: {}", ex.getMessage());
            return ChangeStatus.UNKNOWN;
        }
    }

    public String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private RedisService redisService() {
        try {
            return redisServiceProvider.getIfAvailable();
        } catch (Exception ex) {
            redisUnavailable = true;
            return null;
        }
    }

    public enum ChangeStatus {
        FIRST,
        UNCHANGED,
        CHANGED,
        UNKNOWN
    }
}
