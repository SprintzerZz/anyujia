package com.anyujia.anyujiachatservice.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.anyujia.anyujiachatservice.metrics.ChatMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChatConnectionRegistryTest {
    @Test
    void idleConnectionIsClosedAndUserBecomesOffline() {
        RecordingPresenceService presenceService = new RecordingPresenceService();
        ChatConnectionRegistry registry = new ChatConnectionRegistry(
                presenceService, new ChatNodeIdentity("node-1"), metrics());
        StubConnection connection = new StubConnection("test", 2L);
        registry.add(connection);
        connection.lastActivityMillis = 0;

        Set<Long> offlineUsers = registry.closeIdleSessions(System.currentTimeMillis() - 1L);

        assertThat(offlineUsers).containsExactly(2L);
        assertThat(connection.closeCode).isEqualTo(4000);
        assertThat(connection.open).isFalse();
        assertThat(presenceService.offlineUsers).containsExactly(2L);
    }

    @Test
    void pushUsesConnectionOutboundQueueContract() {
        RecordingPresenceService presenceService = new RecordingPresenceService();
        ChatConnectionRegistry registry = new ChatConnectionRegistry(
                presenceService, new ChatNodeIdentity("node-1"), metrics());
        StubConnection connection = new StubConnection("test", 2L);
        registry.add(connection);

        registry.send(2L, "payload");

        assertThat(connection.payloads).containsExactly("payload");
    }

    @Test
    void removingOneOfMultipleConnectionsKeepsUserOnline() {
        RecordingPresenceService presenceService = new RecordingPresenceService();
        ChatConnectionRegistry registry = new ChatConnectionRegistry(
                presenceService, new ChatNodeIdentity("node-1"), metrics());
        StubConnection first = new StubConnection("first", 2L);
        StubConnection second = new StubConnection("second", 2L);
        registry.add(first);
        registry.add(second);

        registry.remove(first);

        assertThat(presenceService.offlineUsers).isEmpty();
        registry.remove(second);
        assertThat(presenceService.offlineUsers).containsExactly(2L);
    }

    private static final class RecordingPresenceService extends ChatPresenceService {
        private final Set<Long> offlineUsers = new java.util.HashSet<>();

        private RecordingPresenceService() { super(null); }
        @Override public void online(Long userId, String nodeId) { }
        @Override public void heartbeat(Long userId, String nodeId) { }
        @Override public void offline(Long userId, String nodeId) { offlineUsers.add(userId); }
    }

    private ChatMetrics metrics() {
        return new ChatMetrics(new SimpleMeterRegistry());
    }

    private static final class StubConnection implements ChatConnection {
        private final String id;
        private final Long userId;
        private final List<String> payloads = new ArrayList<>();
        private boolean open = true;
        private long lastActivityMillis;
        private int closeCode;

        private StubConnection(String id, Long userId) { this.id = id; this.userId = userId; }
        @Override public String id() { return id; }
        @Override public Long userId() { return userId; }
        @Override public boolean isOpen() { return open; }
        @Override public void touch() { lastActivityMillis = System.currentTimeMillis(); }
        @Override public long lastActivityMillis() { return lastActivityMillis; }
        @Override public void send(String payload) { payloads.add(payload); }
        @Override public void close(int code, String reason) { closeCode = code; open = false; }
    }
}
