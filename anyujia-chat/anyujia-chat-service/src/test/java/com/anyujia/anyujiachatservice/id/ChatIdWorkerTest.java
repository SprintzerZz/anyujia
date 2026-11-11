package com.anyujia.anyujiachatservice.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ChatIdWorkerTest {
    @Test
    void retriesShortClockRollback() {
        AtomicInteger calls = new AtomicInteger();
        long id = ChatIdWorker.retryOnClockRollback(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("Clock moved backwards. Refusing to generate id for 200 milliseconds");
            }
            return 42L;
        });
        assertEquals(42L, id);
        assertEquals(3, calls.get());
    }

    @Test
    void doesNotRetryOtherFailures() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> ChatIdWorker.retryOnClockRollback(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("Database unavailable");
        }));
        assertEquals(1, calls.get());
    }
}
