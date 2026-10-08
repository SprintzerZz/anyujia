package com.anyujia.anyujiachatservice.realtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

public final class SpringChatConnection implements ChatConnection {
    private static final int SLOW_CONSUMER_CODE = 4008;

    private final Long userId;
    private final WebSocketSession session;
    private final Executor sendExecutor;
    private final int maxQueuedMessages;
    private final long maxQueuedBytes;
    private final Deque<QueuedMessage> outbound = new ArrayDeque<>();
    private volatile long lastActivityMillis;
    private long queuedBytes;
    private boolean draining;

    SpringChatConnection(Long userId, WebSocketSession session, Executor sendExecutor,
                         int maxQueuedMessages, long maxQueuedBytes) {
        this.userId = userId;
        this.session = session;
        this.sendExecutor = sendExecutor;
        this.maxQueuedMessages = maxQueuedMessages;
        this.maxQueuedBytes = maxQueuedBytes;
    }

    @Override
    public String id() {
        return session.getId();
    }

    @Override
    public Long userId() {
        return userId;
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public void touch() {
        lastActivityMillis = System.currentTimeMillis();
    }

    @Override
    public long lastActivityMillis() {
        return lastActivityMillis;
    }

    @Override
    public void send(String payload) {
        int bytes = payload.getBytes(StandardCharsets.UTF_8).length;
        boolean schedule = false;
        boolean overflow = false;
        synchronized (outbound) {
            if (!session.isOpen()) {
                return;
            }
            if (outbound.size() >= maxQueuedMessages || queuedBytes + bytes > maxQueuedBytes) {
                overflow = true;
            } else {
                outbound.addLast(new QueuedMessage(payload, bytes));
                queuedBytes += bytes;
                if (!draining) {
                    draining = true;
                    schedule = true;
                }
            }
        }
        if (overflow) {
            close(SLOW_CONSUMER_CODE, "Outbound queue limit exceeded");
        } else if (schedule) {
            try {
                sendExecutor.execute(this::drain);
            } catch (RuntimeException ex) {
                close(SLOW_CONSUMER_CODE, "Outbound executor overloaded");
            }
        }
    }

    private void drain() {
        while (session.isOpen()) {
            QueuedMessage next;
            synchronized (outbound) {
                next = outbound.pollFirst();
                if (next == null) {
                    draining = false;
                    return;
                }
                queuedBytes -= next.bytes();
            }
            try {
                session.sendMessage(new TextMessage(next.payload()));
            } catch (IOException ex) {
                close(CloseStatus.SERVER_ERROR.getCode(), "WebSocket send failed");
                return;
            }
        }
        synchronized (outbound) {
            outbound.clear();
            queuedBytes = 0;
            draining = false;
        }
    }

    @Override
    public void close(int code, String reason) {
        try {
            if (session.isOpen()) {
                session.close(new CloseStatus(code, reason));
            }
        } catch (IOException ignored) {
            // The WebSocket close callback or idle reaper removes the connection.
        }
    }

    private record QueuedMessage(String payload, int bytes) {
    }
}
