package com.anyujia.anyujialegalservice.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.ObjectProvider;

class LegalEmbeddingCacheTest {
    @Test
    @SuppressWarnings("unchecked")
    void sharesCachedVectorAcrossLocalCacheInstancesThroughRedis() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBucket<String> bucket = mock(RBucket.class);
        AtomicReference<String> stored = new AtomicReference<>();
        when(redissonClient.<String>getBucket(anyString(), any(StringCodec.class))).thenReturn(bucket);
        when(bucket.get()).thenAnswer(ignored -> stored.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return null;
        }).when(bucket).set(anyString(), any(java.time.Duration.class));
        LegalAiProperties properties = new LegalAiProperties();
        properties.getEmbedding().setCacheEnabled(true);
        ObjectMapper objectMapper = new ObjectMapper();

        new LegalEmbeddingCache(properties, redissonClient, objectMapper)
                .put("model:1024", "same-risk-query", new double[]{0.25D, 0.75D});
        var restored = new LegalEmbeddingCache(properties, redissonClient, objectMapper)
                .get("model:1024", "same-risk-query");

        assertThat(restored).isPresent();
        assertThat(restored.orElseThrow()).containsExactly(0.25D, 0.75D);
    }

    @Test
    void fallsBackToLocalCacheWhenRedissonCannotBeCreated() {
        LegalAiProperties properties = new LegalAiProperties();
        properties.getEmbedding().setCacheEnabled(true);
        ObjectProvider<RedissonClient> failingProvider = new ObjectProvider<>() {
            @Override
            public RedissonClient getObject(Object... args) {
                throw new IllegalStateException("Redis requires authentication");
            }

            @Override
            public RedissonClient getIfAvailable() {
                throw new IllegalStateException("Redis requires authentication");
            }

            @Override
            public RedissonClient getIfUnique() {
                throw new IllegalStateException("Redis requires authentication");
            }

            @Override
            public RedissonClient getObject() {
                throw new IllegalStateException("Redis requires authentication");
            }
        };

        LegalEmbeddingCache cache = new LegalEmbeddingCache(properties, failingProvider, new ObjectMapper());

        cache.put("model:1024", "same-risk-query", new double[]{0.5D, 0.5D});
        var restored = cache.get("model:1024", "same-risk-query");

        assertThat(restored).isPresent();
        assertThat(restored.orElseThrow()).containsExactly(0.5D, 0.5D);
    }
}
