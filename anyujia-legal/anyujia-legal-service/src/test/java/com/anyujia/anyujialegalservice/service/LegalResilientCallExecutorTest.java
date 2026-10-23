package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

class LegalResilientCallExecutorTest {
    @Test
    void isolatesCircuitBreakersByDependency() {
        LegalResilientCallExecutor executor = new LegalResilientCallExecutor();

        String failed = executor.execute("legal-entailment", () -> {
            throw new IllegalStateException("down");
        }, () -> "fallback");
        String healthy = executor.execute("legal-reranker", () -> "ok", () -> "fallback");

        assertThat(failed).isEqualTo("fallback");
        assertThat(healthy).isEqualTo("ok");
    }

    @Test
    void doesNotRetryClientErrors() {
        LegalResilientCallExecutor executor = new LegalResilientCallExecutor();
        AtomicInteger calls = new AtomicInteger();

        Optional<String> result = executor.execute("legal-entailment", () -> {
            calls.incrementAndGet();
            throw HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "bad request", null, null, null);
        }, Optional::empty);

        assertThat(result).isEmpty();
        assertThat(calls).hasValue(1);
    }
}
