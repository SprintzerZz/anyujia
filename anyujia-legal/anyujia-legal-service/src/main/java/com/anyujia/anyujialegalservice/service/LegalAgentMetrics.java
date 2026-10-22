package com.anyujia.anyujialegalservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegalAgentMetrics {
    private final MeterRegistry registry;

    public void recordPublished() {
        Counter.builder("legal.agent.events.published")
                .description("Legal Agent events published to Redis Stream")
                .register(registry).increment();
    }

    public void recordPublishFailure() {
        Counter.builder("legal.agent.events.publish.failure")
                .description("Legal Agent Redis Stream publish failures")
                .register(registry).increment();
    }

    public void recordCompensation(int count) {
        Counter.builder("legal.agent.events.compensation")
                .description("Legal Agent events retried by compensation")
                .register(registry).increment(count);
    }

    public void recordStreamNotification() {
        Counter.builder("legal.agent.sse.stream.notification")
                .description("Legal Agent SSE notifications received from Redis Stream")
                .register(registry).increment();
    }

    public void recordSseQueueOverflow() {
        Counter.builder("legal.agent.sse.queue.overflow")
                .description("Legal Agent SSE wake-up queue overflows")
                .register(registry).increment();
    }

    public void recordSseWriterRejected() {
        Counter.builder("legal.agent.sse.writer.rejected")
                .description("Legal Agent SSE writer executor rejections")
                .register(registry).increment();
    }

    public void recordSseWrite(long nanos) {
        Timer.builder("legal.agent.sse.write")
                .description("Legal Agent SSE write duration")
                .register(registry).record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    public void recordSubmitStage(String stage, long nanos) {
        Timer.builder("legal.agent.submit")
                .description("Legal Agent enqueue stage duration")
                .tag("stage", stage)
                .publishPercentiles(0.95, 0.99)
                .register(registry).record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    public void recordSubmitRejected() {
        Counter.builder("legal.agent.submit.rejected")
                .description("Legal Agent enqueue requests rejected by admission control")
                .register(registry).increment();
    }
}
