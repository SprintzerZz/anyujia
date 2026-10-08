package com.anyujia.anyujiachatservice.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

class SpringChatConnectionTest {
    @Test
    void sendsQueuedMessagesInOrder() {
        StubSession session = new StubSession();
        SpringChatConnection connection = new SpringChatConnection(1L, session, Runnable::run, 8, 1024);

        connection.send("first");
        connection.send("second");

        assertThat(session.payloads).containsExactly("first", "second");
    }

    @Test
    void closesSlowConsumerWhenPerSessionQueueIsFull() {
        StubSession session = new StubSession();
        List<Runnable> tasks = new ArrayList<>();
        Executor pausedExecutor = tasks::add;
        SpringChatConnection connection = new SpringChatConnection(1L, session, pausedExecutor, 1, 1024);

        connection.send("first");
        connection.send("second");

        assertThat(tasks).hasSize(1);
        assertThat(session.closeStatus.getCode()).isEqualTo(4008);
        assertThat(session.open).isFalse();
    }

    private static final class StubSession implements WebSocketSession {
        private final Map<String, Object> attributes = new HashMap<>();
        private final List<String> payloads = new ArrayList<>();
        private boolean open = true;
        private CloseStatus closeStatus;

        @Override public String getId() { return "test"; }
        @Override public URI getUri() { return URI.create("ws://localhost/chat/websocket"); }
        @Override public HttpHeaders getHandshakeHeaders() { return HttpHeaders.EMPTY; }
        @Override public Map<String, Object> getAttributes() { return attributes; }
        @Override public Principal getPrincipal() { return null; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public String getAcceptedProtocol() { return null; }
        @Override public void setTextMessageSizeLimit(int messageSizeLimit) { }
        @Override public int getTextMessageSizeLimit() { return 64 * 1024; }
        @Override public void setBinaryMessageSizeLimit(int messageSizeLimit) { }
        @Override public int getBinaryMessageSizeLimit() { return 64 * 1024; }
        @Override public List<WebSocketExtension> getExtensions() { return Collections.emptyList(); }
        @Override public void sendMessage(WebSocketMessage<?> message) { payloads.add((String) message.getPayload()); }
        @Override public boolean isOpen() { return open; }
        @Override public void close() throws IOException { close(CloseStatus.NORMAL); }
        @Override public void close(CloseStatus status) { closeStatus = status; open = false; }
    }
}
