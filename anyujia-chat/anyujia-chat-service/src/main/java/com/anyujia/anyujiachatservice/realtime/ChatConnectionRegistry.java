package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiachatservice.metrics.ChatMetrics;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatConnectionRegistry {
    private final ConcurrentHashMap<Long, Set<ChatConnection>> connections = new ConcurrentHashMap<>();
    private final ChatPresenceService presenceService;
    private final ChatNodeIdentity nodeIdentity;
    private final ChatMetrics chatMetrics;

    public void add(ChatConnection connection) {
        addLocal(connection);
        announceOnline(connection);
    }

    public void heartbeat(ChatConnection connection) {
        connection.touch();
        announceHeartbeat(connection);
    }

    public void addLocal(ChatConnection connection) {
        connection.touch();
        boolean added = connections.computeIfAbsent(connection.userId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(connection);
        if (added) {
            chatMetrics.connectionOpened();
        }
    }

    public void announceOnline(ChatConnection connection) {
        presenceService.online(connection.userId(), nodeIdentity.getNodeId());
    }

    public void announceHeartbeat(ChatConnection connection) {
        presenceService.heartbeat(connection.userId(), nodeIdentity.getNodeId());
    }

    public boolean remove(ChatConnection connection) {
        boolean becameOffline = removeLocal(connection);
        if (becameOffline) {
            announceOffline(connection);
        }
        return becameOffline;
    }

    public boolean removeLocal(ChatConnection connection) {
        AtomicBoolean becameOffline = new AtomicBoolean();
        connections.computeIfPresent(connection.userId(), (ignored, sessions) -> {
            if (sessions.remove(connection)) {
                chatMetrics.connectionClosed();
            }
            if (sessions.isEmpty()) {
                becameOffline.set(true);
                return null;
            }
            return sessions;
        });
        return becameOffline.get();
    }

    public void announceOffline(ChatConnection connection) {
        presenceService.offline(connection.userId(), nodeIdentity.getNodeId());
    }

    public Set<Long> closeIdleSessions(long idleBeforeEpochMillis) {
        Set<Long> offlineUsers = new HashSet<>();
        connections.forEach((userId, sessions) -> sessions.forEach(connection -> {
            if (connection.managesOwnIdleTimeout()
                    || connection.lastActivityMillis() >= idleBeforeEpochMillis) {
                return;
            }
            connection.close(4000, "Heartbeat timeout");
            if (remove(connection)) {
                offlineUsers.add(userId);
            }
        }));
        return offlineUsers;
    }

    public void send(Long userId, String payload) {
        Set<ChatConnection> sessions = connections.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.forEach(connection -> {
            if (connection.isOpen()) {
                connection.send(payload);
            } else {
                remove(connection);
            }
        });
    }
}
