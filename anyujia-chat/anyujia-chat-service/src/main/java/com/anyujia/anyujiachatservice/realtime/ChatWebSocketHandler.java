package com.anyujia.anyujiachatservice.realtime;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final ChatConnectionRegistry registry;
    private final ChatFrameProcessor frameProcessor;
    private final SpringChatConnectionFactory connectionFactory;
    private final ConcurrentHashMap<String, SpringChatConnection> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SpringChatConnection connection = connectionFactory.create(userId(session), session);
        sessions.put(session.getId(), connection);
        registry.add(connection);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SpringChatConnection connection = sessions.get(session.getId());
        if (connection == null) {
            return;
        }
        registry.heartbeat(connection);
        frameProcessor.process(connection.userId(), message.getPayload(), connection::send);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SpringChatConnection connection = sessions.remove(session.getId());
        if (connection != null) {
            registry.remove(connection);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        SpringChatConnection connection = sessions.remove(session.getId());
        if (connection != null) {
            registry.remove(connection);
        }
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private Long userId(WebSocketSession session) {
        return (Long) session.getAttributes().get("userId");
    }
}
