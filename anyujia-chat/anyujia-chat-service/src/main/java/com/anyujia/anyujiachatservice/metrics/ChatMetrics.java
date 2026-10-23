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
    private final Timer persistLatencyTimer;
    private final Timer requireCounterpartyTimer;
    private final Timer findClientTimer;
    private final Timer sequenceTimer;
    private final Timer rateLimitTimer;
    private final Counter pushFailureCounter;
    private final Counter rateLimitedCounter;
    private final AtomicInteger onlineConnections = new AtomicInteger();
    private final AtomicInteger outboxPendingRows = new AtomicInteger();
    private final AtomicInteger outboxProcessingRows = new AtomicInteger();
    private final AtomicInteger persistQueueDepth = new AtomicInteger();
    private final AtomicInteger persistDlqDepth = new AtomicInteger();

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
        this.persistLatencyTimer = Timer.builder("chat_persist_latency")
                .description("End-to-end persistence latency from MQ consume to committed ACK")
                .register(registry);
        this.requireCounterpartyTimer = Timer.builder("chat_message_require_counterparty_duration")
                .description("Chat message counterparty lookup duration")
                .register(registry);
        this.findClientTimer = Timer.builder("chat_message_find_client_duration")
                .description("Chat message client-message-id idempotency lookup duration")
                .register(registry);
        this.sequenceTimer = Timer.builder("chat_message_sequence_duration")
                .description("Chat conversation sequence allocation duration")
                .register(registry);
        this.rateLimitTimer = Timer.builder("chat_message_rate_limit_duration")
                .description("Chat conversation rate-limit check duration")
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
        registry.gauge("chat_outbox_pending_rows",
                outboxPendingRows,
                AtomicInteger::get);
        registry.gauge("chat_outbox_processing_rows",
                outboxProcessingRows,
                AtomicInteger::get);
        registry.gauge("chat_persist_queue_depth",
                persistQueueDepth,
                AtomicInteger::get);
        registry.gauge("chat_persist_dlq_depth",
                persistDlqDepth,
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

    public void recordPersistLatency(long startNanos) {
        persistLatencyTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    public void recordRequireCounterparty(long startNanos) {
        requireCounterpartyTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    public void recordFindClient(long startNanos) {
        findClientTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    public void recordSequence(long startNanos) {
        sequenceTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    public void recordRateLimit(long startNanos) {
        rateLimitTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    public void setOutboxBacklog(int pending, int processing) {
        outboxPendingRows.set(pending);
        outboxProcessingRows.set(processing);
    }

    public void setPersistQueueDepths(int queueDepth, int dlqDepth) {
        persistQueueDepth.set(queueDepth);
        persistDlqDepth.set(dlqDepth);
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
