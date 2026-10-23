package com.anyujia.anyujiacouponservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponReceiveCacheServiceTest {

    @Test
    void reserveRetriesTransientRedisFailureBeforeFastFail() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CouponReceiveCacheService service = new CouponReceiveCacheService(redisTemplate);
        ReflectionTestUtils.setField(service, "retryAttempts", 2);
        ReflectionTestUtils.setField(service, "retryBackoffMillis", 0L);
        ReflectionTestUtils.setField(service, "fastFailMillis", 1000L);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("master down"));

        CouponReceiveCacheService.ReserveResult result =
                service.reserve(10L, 1, 100L);

        assertEquals(CouponReceiveCacheService.ReserveResult.REDIS_UNAVAILABLE, result);
        verify(redisTemplate, times(3))
                .execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void reserveSucceedsWhenTransientRedisFailureRecoversOnRetry() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CouponReceiveCacheService service = new CouponReceiveCacheService(redisTemplate);
        ReflectionTestUtils.setField(service, "retryAttempts", 2);
        ReflectionTestUtils.setField(service, "retryBackoffMillis", 0L);
        ReflectionTestUtils.setField(service, "fastFailMillis", 1000L);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("master switching"))
                .thenReturn(0L);

        CouponReceiveCacheService.ReserveResult result =
                service.reserve(10L, 1, 100L);

        assertEquals(CouponReceiveCacheService.ReserveResult.SUCCESS, result);
        verify(redisTemplate, times(2))
                .execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void reserveFastFailsWhileRedisUnavailableWindowIsOpen() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CouponReceiveCacheService service = new CouponReceiveCacheService(redisTemplate);
        ReflectionTestUtils.setField(service, "retryAttempts", 0);
        ReflectionTestUtils.setField(service, "retryBackoffMillis", 0L);
        ReflectionTestUtils.setField(service, "fastFailMillis", 1000L);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("master down"));

        service.reserve(10L, 1, 100L);
        CouponReceiveCacheService.ReserveResult second =
                service.reserve(10L, 1, 101L);

        assertEquals(CouponReceiveCacheService.ReserveResult.REDIS_UNAVAILABLE, second);
        verify(redisTemplate, times(1))
                .execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void reserveDoesNotRetryNonTransientFailure() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CouponReceiveCacheService service = new CouponReceiveCacheService(redisTemplate);
        ReflectionTestUtils.setField(service, "retryAttempts", 2);
        ReflectionTestUtils.setField(service, "retryBackoffMillis", 0L);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("bad script result"))
                .thenReturn(0L);

        assertThrows(IllegalStateException.class,
                () -> service.reserve(10L, 1, 100L));

        CouponReceiveCacheService.ReserveResult second =
                service.reserve(10L, 1, 101L);

        assertEquals(CouponReceiveCacheService.ReserveResult.SUCCESS, second);
        verify(redisTemplate, times(2))
                .execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));
    }
}
