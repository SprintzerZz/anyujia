package com.anyujia.anyujiachatservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anyujia.anyujiacommondomain.exception.ServiceException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.test.util.ReflectionTestUtils;

class ChatImageUploadRedisIntegrationTest {
    @Test
    void twoClientsShareUploadLockAndGlobalPermit() throws Exception {
        String password = System.getenv("CHAT_REDIS_TEST_PASSWORD");
        Assumptions.assumeTrue(password != null && !password.isBlank(),
                "Set CHAT_REDIS_TEST_PASSWORD to run against a real Redis server");
        String address = System.getenv().getOrDefault("CHAT_REDIS_TEST_ADDRESS", "redis://127.0.0.1:6379");
        RedissonClient firstClient = connect(address, password);
        RedissonClient secondClient = connect(address, password);
        var executor = Executors.newSingleThreadExecutor();
        try {
            String prefix = "chat:image:integration:" + UUID.randomUUID();
            var first = new ChatImageUploadCoordinator(firstClient);
            var second = new ChatImageUploadCoordinator(secondClient);
            ReflectionTestUtils.setField(first, "redisPrefix", prefix);
            ReflectionTestUtils.setField(second, "redisPrefix", prefix);
            ReflectionTestUtils.setField(first, "globalMaxConcurrentUploads", 1);
            ReflectionTestUtils.setField(second, "globalMaxConcurrentUploads", 1);

            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            var holder = executor.submit(() -> first.withUploadLock(7L, 12L, "same-upload", () -> {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return "first";
            }));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.withUploadLock(7L, 12L, "same-upload", () -> "duplicate"))
                        .isInstanceOf(ServiceException.class);
            } finally {
                release.countDown();
            }
            assertThat(holder.get(10, TimeUnit.SECONDS)).isEqualTo("first");

            CountDownLatch permitted = new CountDownLatch(1);
            CountDownLatch finish = new CountDownLatch(1);
            var inFlight = executor.submit(() -> first.withGlobalPermit(() -> {
                permitted.countDown();
                try {
                    if (!finish.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return "uploaded";
            }));
            try {
                assertThat(permitted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.withGlobalPermit(() -> "overflow"))
                        .isInstanceOf(ServiceException.class);
            } finally {
                finish.countDown();
            }
            assertThat(inFlight.get(10, TimeUnit.SECONDS)).isEqualTo("uploaded");
            assertThat(second.withGlobalPermit(() -> "next")).isEqualTo("next");
        } finally {
            executor.shutdownNow();
            firstClient.shutdown();
            secondClient.shutdown();
        }
    }

    private RedissonClient connect(String address, String password) {
        Config config = new Config();
        config.useSingleServer().setAddress(address).setPassword(password);
        return Redisson.create(config);
    }
}
