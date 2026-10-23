package com.anyujia.anyujialegalservice.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** OpenTelemetry API-only bridge; exporters remain deployment-specific. */
@Component
public class LegalTelemetry {
    private final Tracer tracer = GlobalOpenTelemetry.getTracer("anyujia-legal-agent");

    public <T> T stage(String stage, Long taskId, Supplier<T> action) {
        Span span = tracer.spanBuilder("legal.agent." + stage).setAttribute("legal.task_id", taskId).startSpan();
        try (var scope = span.makeCurrent()) {
            return action.get();
        } catch (RuntimeException ex) {
            span.recordException(ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    public void stage(String stage, Long taskId, Runnable action) {
        stage(stage, taskId, () -> {
            action.run();
            return null;
        });
    }
}
