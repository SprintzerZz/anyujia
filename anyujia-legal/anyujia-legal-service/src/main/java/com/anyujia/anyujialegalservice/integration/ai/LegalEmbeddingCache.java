package com.anyujia.anyujialegalservice.integration.ai;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LegalEmbeddingCache {
    private static final String KEY_PREFIX = "legal:embedding:v1:";
    private final LegalAiProperties properties;
    private final ObjectProvider<RedissonClient> redissonClientProvider;
    private final RedissonClient fixedRedissonClient;
    private final ObjectMapper objectMapper;
    private final Cache<String, double[]> localCache;
    private volatile boolean sharedCacheUnavailable;

    @Autowired
    public LegalEmbeddingCache(LegalAiProperties properties,
                               ObjectProvider<RedissonClient> redissonClientProvider,
                               ObjectMapper objectMapper) {
        this(properties, redissonClientProvider, null, objectMapper);
    }

    LegalEmbeddingCache(LegalAiProperties properties, RedissonClient redissonClient,
                        ObjectMapper objectMapper) {
        this(properties, null, redissonClient, objectMapper);
    }

    private LegalEmbeddingCache(LegalAiProperties properties,
                                ObjectProvider<RedissonClient> redissonClientProvider,
                                RedissonClient fixedRedissonClient,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.redissonClientProvider = redissonClientProvider;
        this.fixedRedissonClient = fixedRedissonClient;
        this.objectMapper = objectMapper;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(properties.getEmbedding().getCacheMaxEntries())
                .expireAfterWrite(properties.getEmbedding().getCacheTtlSeconds(), TimeUnit.SECONDS)
                .build();
    }

    public Optional<double[]> get(String namespace, String text) {
        if (!properties.getEmbedding().isCacheEnabled()) {
            return Optional.empty();
        }
        String key = key(namespace, text);
        double[] local = localCache.getIfPresent(key);
        if (local != null) {
            return Optional.of(local);
        }
        RedissonClient redissonClient = redissonClient();
        if (redissonClient == null) {
            return Optional.empty();
        }
        try {
            String json = (String) redissonClient.getBucket(key, StringCodec.INSTANCE).get();
            double[] remote = json == null ? null : objectMapper.readValue(json, double[].class);
            if (remote != null && remote.length > 0) {
                localCache.put(key, remote);
                return Optional.of(remote);
            }
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Legal embedding Redis cache read failed; continuing without shared cache: {}",
                    ex.getMessage());
        }
        return Optional.empty();
    }

    public void put(String namespace, String text, double[] vector) {
        if (!properties.getEmbedding().isCacheEnabled() || vector == null || vector.length == 0) {
            return;
        }
        String key = key(namespace, text);
        localCache.put(key, vector);
        RedissonClient redissonClient = redissonClient();
        if (redissonClient == null) {
            return;
        }
        try {
            redissonClient.getBucket(key, StringCodec.INSTANCE).set(objectMapper.writeValueAsString(vector),
                    Duration.ofSeconds(properties.getEmbedding().getCacheTtlSeconds()));
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Legal embedding Redis cache write failed; local cache remains available: {}",
                    ex.getMessage());
        }
    }

    private RedissonClient redissonClient() {
        if (fixedRedissonClient != null) {
            return fixedRedissonClient;
        }
        if (sharedCacheUnavailable || redissonClientProvider == null) {
            return null;
        }
        try {
            return redissonClientProvider.getIfAvailable();
        } catch (RuntimeException ex) {
            sharedCacheUnavailable = true;
            log.warn("Legal embedding Redis cache unavailable; local cache remains available: {}",
                    ex.getMessage());
            return null;
        }
    }

    private String key(String namespace, String text) {
        String identity = namespace + ':' + text;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
