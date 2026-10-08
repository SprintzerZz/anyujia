package com.anyujia.anyujiachatservice.realtime;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class SpringChatConnectionFactory {
    private final Executor sendExecutor;
    private final int maxQueuedMessages;
    private final long maxQueuedBytes;

    public SpringChatConnectionFactory(
            @Qualifier("chatWebSocketSendExecutor") Executor sendExecutor,
            @Value("${chat.websocket.outbound.max-queued-messages:256}") int maxQueuedMessages,
            @Value("${chat.websocket.outbound.max-queued-bytes:2097152}") long maxQueuedBytes) {
        this.sendExecutor = sendExecutor;
        this.maxQueuedMessages = maxQueuedMessages;
        this.maxQueuedBytes = maxQueuedBytes;
    }

    public SpringChatConnection create(Long userId, WebSocketSession session) {
        return new SpringChatConnection(userId, session, sendExecutor,
                maxQueuedMessages, maxQueuedBytes);
    }
}
