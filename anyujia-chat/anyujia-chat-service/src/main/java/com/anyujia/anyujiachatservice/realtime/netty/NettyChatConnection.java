package com.anyujia.anyujiachatservice.realtime.netty;

import com.anyujia.anyujiachatservice.realtime.ChatConnection;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

final class NettyChatConnection implements ChatConnection {
    private static final int SLOW_CONSUMER_CODE = 4008;

    private final Long userId;
    private final Channel channel;
    private final NettyIdleTimer idleTimer;
    private final long idleTimeoutMillis;
    private final int maxQueuedMessages;
    private final long maxQueuedBytes;
    private final Deque<QueuedFrame> outbound = new ArrayDeque<>();
    private final Object reservationLock = new Object();
    private volatile long lastActivityMillis;
    private int reservedMessages;
    private long reservedBytes;
    private boolean idleCheckScheduled;

    NettyChatConnection(Long userId, Channel channel, NettyIdleTimer idleTimer,
                        long idleTimeoutMillis, int maxQueuedMessages, long maxQueuedBytes) {
        this.userId = userId;
        this.channel = channel;
        this.idleTimer = idleTimer;
        this.idleTimeoutMillis = idleTimeoutMillis;
        this.maxQueuedMessages = maxQueuedMessages;
        this.maxQueuedBytes = maxQueuedBytes;
        touch();
        scheduleIdleCheck(idleTimeoutMillis);
    }

    @Override
    public String id() {
        return channel.id().asLongText();
    }

    @Override
    public Long userId() {
        return userId;
    }

    @Override
    public boolean isOpen() {
        return channel.isActive();
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
    public boolean managesOwnIdleTimeout() {
        return true;
    }

    @Override
    public void send(String payload) {
        int bytes = payload.getBytes(StandardCharsets.UTF_8).length;
        if (!reserve(bytes)) {
            close(SLOW_CONSUMER_CODE, "Outbound queue limit exceeded");
            return;
        }
        channel.eventLoop().execute(() -> {
            if (!channel.isActive()) {
                releaseReservation(bytes);
                return;
            }
            outbound.addLast(new QueuedFrame(new TextWebSocketFrame(payload), bytes));
            drain();
        });
    }

    void drain() {
        if (!channel.eventLoop().inEventLoop()) {
            channel.eventLoop().execute(this::drain);
            return;
        }
        boolean wrote = false;
        while (channel.isActive() && channel.isWritable()) {
            QueuedFrame queued = outbound.pollFirst();
            if (queued == null) {
                break;
            }
            releaseReservation(queued.bytes());
            channel.write(queued.frame());
            wrote = true;
        }
        if (wrote) {
            channel.flush();
        }
    }

    void discardQueuedFrames() {
        QueuedFrame queued;
        while ((queued = outbound.pollFirst()) != null) {
            releaseReservation(queued.bytes());
            ReferenceCountUtil.release(queued.frame());
        }
    }

    @Override
    public void close(int code, String reason) {
        channel.eventLoop().execute(() -> {
            if (channel.isActive()) {
                channel.writeAndFlush(new CloseWebSocketFrame(code, reason))
                        .addListener(ignored -> channel.close());
            }
        });
    }

    private boolean reserve(int bytes) {
        synchronized (reservationLock) {
            if (!channel.isActive() || reservedMessages >= maxQueuedMessages
                    || reservedBytes + bytes > maxQueuedBytes) {
                return false;
            }
            reservedMessages++;
            reservedBytes += bytes;
            return true;
        }
    }

    private void releaseReservation(int bytes) {
        synchronized (reservationLock) {
            reservedMessages--;
            reservedBytes -= bytes;
        }
    }

    private void scheduleIdleCheck(long delayMillis) {
        synchronized (reservationLock) {
            if (!idleCheckScheduled) {
                idleCheckScheduled = true;
                idleTimer.schedule(this::checkIdle, delayMillis);
            }
        }
    }

    private void checkIdle() {
        synchronized (reservationLock) {
            idleCheckScheduled = false;
        }
        if (!channel.isActive()) {
            return;
        }
        long remaining = idleTimeoutMillis - (System.currentTimeMillis() - lastActivityMillis);
        if (remaining <= 0) {
            close(4000, "Heartbeat timeout");
        } else {
            scheduleIdleCheck(remaining);
        }
    }

    private record QueuedFrame(TextWebSocketFrame frame, int bytes) {
    }
}
