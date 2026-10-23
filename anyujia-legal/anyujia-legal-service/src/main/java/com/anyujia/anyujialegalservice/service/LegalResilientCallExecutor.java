package com.anyujia.anyujialegalservice.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Small dependency-neutral boundary for Feign/AI/vector calls. */
@Component
public class LegalResilientCallExecutor {
    private final CircuitBreaker breaker = CircuitBreaker.ofDefaults("legal-external-dependencies");

    public <T> T execute(String dependency, Callable<T> call, Supplier<T> fallback) {
        try {
            return CircuitBreaker.decorateCallable(breaker, () -> retry(call, 2)).call();
        } catch (Exception ex) {
            return fallback.get();
        }
    }

    private <T> T retry(Callable<T> call, int attempts) throws Exception {
        Exception last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                return call.call();
            } catch (Exception ex) {
                last = ex;
                if (i + 1 < attempts) {
                    Thread.sleep(Duration.ofMillis(50L << i).toMillis());
                }
            }
        }
        throw last;
    }
}
