package com.anyujia.anyujialegalservice.integration.ai;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DashScopeCallGuard {
    public static final String EMBEDDING = "embedding";
    public static final String CHAT = "chat";

    private final LegalAiProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    @Autowired
    public DashScopeCallGuard(LegalAiProperties properties) {
        this(properties, Clock.systemUTC());
    }

    DashScopeCallGuard(LegalAiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean allowRequest(String channel) {
        return clock.millis() >= state(channel).blockedUntilMillis.get();
    }

    public void recordSuccess(String channel) {
        State state = state(channel);
        state.consecutiveFailures.set(0);
        state.blockedUntilMillis.set(0L);
    }

    public void recordFailure(String channel) {
        State state = state(channel);
        if (state.consecutiveFailures.incrementAndGet() >= properties.getFailureThreshold()) {
            state.blockedUntilMillis.set(clock.millis() + properties.getCooldownSeconds() * 1000L);
            state.consecutiveFailures.set(0);
        }
    }

    private State state(String channel) {
        return states.computeIfAbsent(channel, ignored -> new State());
    }

    private static final class State {
        private final AtomicInteger consecutiveFailures = new AtomicInteger();
        private final AtomicLong blockedUntilMillis = new AtomicLong();
    }
}
