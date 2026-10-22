package com.anyujia.anyujialegalservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
}
