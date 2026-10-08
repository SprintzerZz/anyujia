package com.anyujia.anyujiachatservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class ChatMetrics {
    private final Timer messageSendTimer;
    private final Timer messageSyncTimer;
    private final Timer messageDbWriteTimer;
    private final Counter pushFailureCounter;
    private final Counter rateLimitedCounter;
    private final AtomicInteger onlineConnections = new AtomicInteger();

    public ChatMetrics(MeterRegistry registry) {
        this.messageSendTimer = Timer.builder("chat_message_send_duration")
                .description("End-to-end chat message send duration")
                .register(registry);
        this.messageSyncTimer = Timer.builder("chat_message_sync_duration")
                .description("Chat message sync duration")
                .register(registry);
        this.messageDbWriteTimer = Timer.builder("chat_message_db_write_duration")
                .description("Chat message persistence and conversation index update duration")
                .register(registry);
        this.pushFailureCounter = Counter.builder("chat_message_push_failures_total")
                .description("Persisted chat messages whose online push failed")
                .register(registry);
        this.rateLimitedCounter = Counter.builder("chat_conversation_rate_limited_total")
                .description("Messages rejected by conversation hot-spot rate limiting")
                .register(registry);
        registry.gauge("chat_websocket_online_connections",
                onlineConnections,
                AtomicInteger::get);
    }

    public void recordSend(long startNanos) {
        messageSendTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    public void recordSync(long startNanos) {
        messageSyncTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    public void recordDbWrite(long startNanos) {
        messageDbWriteTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    public void incrementPushFailure() {
        pushFailureCounter.increment();
    }

    public void incrementRateLimited() {
        rateLimitedCounter.increment();
    }

    public void connectionOpened() {
        onlineConnections.incrementAndGet();
    }

    public void connectionClosed() {
        onlineConnections.updateAndGet(value -> Math.max(0, value - 1));
    }
}
