package com.anyujia.anyujiachatservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anyujia.anyujiacommondomain.exception.ServiceException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

class ChatImageUploadCoordinatorTest {
    private final RedissonClient redis = mock(RedissonClient.class);
    private final ChatImageUploadCoordinator coordinator = new ChatImageUploadCoordinator(redis);

    @Test
    void duplicateUploadLockRunsOnlyForOwnerAndReleases() throws Exception {
        RLock lock = mock(RLock.class);
        when(redis.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        assertThat(coordinator.withUploadLock(7L, 12L, "upload-1", () -> "stored")).isEqualTo("stored");

        verify(lock).unlock();
    }

    @Test
    void globalPermitIsReleasedAfterUpload() {
        RScript script = mock(RScript.class);
        when(redis.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER),
                anyList(), any(Object[].class))).thenReturn(1L);

        assertThat(coordinator.withGlobalPermit(() -> "stored")).isEqualTo("stored");

        verify(script, times(2)).eval(eq(RScript.Mode.READ_WRITE), anyString(),
                eq(RScript.ReturnType.INTEGER), anyList(), any(Object[].class));
    }

    @Test
    void fullGlobalLimitRejectsUpload() {
        RScript script = mock(RScript.class);
        when(redis.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER),
                anyList(), any(Object[].class))).thenReturn(0L);

        assertThatThrownBy(() -> coordinator.withGlobalPermit(() -> "never"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Too many image uploads");
    }
}
