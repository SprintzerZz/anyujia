package com.anyujia.anyujialegalservice.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Small dependency-neutral boundary for Feign/AI/vector calls. */
@Component
public class LegalResilientCallExecutor {
    private final CircuitBreakerRegistry breakerRegistry = CircuitBreakerRegistry.ofDefaults();
    private final ConcurrentHashMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public LegalResilientCallExecutor() {
        this(new SimpleMeterRegistry());
    }

    @Autowired
    public LegalResilientCallExecutor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> T execute(String dependency, Callable<T> call, Supplier<T> fallback) {
        String breakerName = normalize(dependency);
        CircuitBreaker breaker = breakers.computeIfAbsent(breakerName,
                name -> breakerRegistry.circuitBreaker("legal-" + name));
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = CircuitBreaker.decorateCallable(breaker, () -> retry(call, breakerName)).call();
            meterRegistry.counter("legal.resilience.success", "dependency", breakerName).increment();
            return result;
        } catch (Exception ex) {
            meterRegistry.counter("legal.resilience.fallback", "dependency", breakerName).increment();
            return fallback.get();
        } finally {
            sample.stop(Timer.builder("legal.resilience.duration")
                    .tag("dependency", breakerName)
                    .publishPercentiles(0.95, 0.99)
                    .register(meterRegistry));
        }
    }

    public boolean isOpen(String dependency) {
        CircuitBreaker breaker = breakers.get(normalize(dependency));
        return breaker != null && breaker.getState() == CircuitBreaker.State.OPEN;
    }

    private <T> T retry(Callable<T> call, String dependency) throws Exception {
        int attempts = 2;
        Exception last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                return call.call();
            } catch (HttpClientErrorException ex) {
                // Invalid requests and authentication/permission failures are not transient.
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            } catch (Exception ex) {
                last = ex;
                meterRegistry.counter("legal.resilience.retry", "dependency", dependency).increment();
                if (i + 1 < attempts) {
                    Thread.sleep(Duration.ofMillis(50L << i).toMillis());
                }
            }
        }
        throw last;
    }

    private String normalize(String dependency) {
        if (dependency == null || dependency.isBlank()) {
            return "unknown";
        }
        return dependency.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-");
    }
}
