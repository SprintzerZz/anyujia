package com.anyujia.anyujiachatservice.realtime.netty;

import com.anyujia.anyujiachatservice.realtime.ChatConnection;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.Timeout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

final class NettyChatConnection implements ChatConnection {
    private static final int SLOW_CONSUMER_CODE = 4008;

    private final Long userId;
    private final Channel channel;
    private final NettyIdleTimer idleTimer;
    private final long idleTimeoutMillis;
    private final int maxQueuedMessages;
    private final long maxQueuedBytes;
    private final LongSupplier clock;
    private final Deque<QueuedFrame> outbound = new ArrayDeque<>();
    private final Object reservationLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile long lastActivityMillis;
    private int reservedMessages;
    private long reservedBytes;
    private Timeout idleCheckTimeout;

    NettyChatConnection(Long userId, Channel channel, NettyIdleTimer idleTimer,
                        long idleTimeoutMillis, int maxQueuedMessages, long maxQueuedBytes) {
        this(userId, channel, idleTimer, idleTimeoutMillis, maxQueuedMessages, maxQueuedBytes,
                System::currentTimeMillis);
    }

    NettyChatConnection(Long userId, Channel channel, NettyIdleTimer idleTimer,
                        long idleTimeoutMillis, int maxQueuedMessages, long maxQueuedBytes,
                        LongSupplier clock) {
        this.userId = userId;
        this.channel = channel;
        this.idleTimer = idleTimer;
        this.idleTimeoutMillis = idleTimeoutMillis;
        this.maxQueuedMessages = maxQueuedMessages;
        this.maxQueuedBytes = maxQueuedBytes;
        this.clock = clock;
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
        return !closed.get() && channel.isActive();
    }

    @Override
    public void touch() {
        if (!closed.get()) {
            lastActivityMillis = clock.getAsLong();
        }
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
        if (closed.get()) {
            return;
        }
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
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (reservationLock) {
            if (idleCheckTimeout != null) {
                idleCheckTimeout.cancel();
                idleCheckTimeout = null;
            }
        }
        channel.eventLoop().execute(() -> {
            discardQueuedFrames();
            if (channel.isActive()) {
                channel.writeAndFlush(new CloseWebSocketFrame(code, reason))
                        .addListener(ignored -> channel.close());
            }
        });
    }

    private boolean reserve(int bytes) {
        synchronized (reservationLock) {
            if (closed.get() || !channel.isActive() || reservedMessages >= maxQueuedMessages
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
            if (closed.get() || (idleCheckTimeout != null && !idleCheckTimeout.isCancelled())) {
                return;
            }
            idleCheckTimeout = idleTimer.schedule(this::checkIdle, delayMillis);
        }
    }

    void checkIdle() {
        synchronized (reservationLock) {
            idleCheckTimeout = null;
        }
        if (closed.get() || !channel.isActive()) {
            return;
        }
        long activityAt = lastActivityMillis;
        long remaining = idleTimeoutMillis - (clock.getAsLong() - activityAt);
        if (remaining <= 0) {
            closeIfStillIdle(activityAt);
        } else {
            scheduleIdleCheck(remaining);
        }
    }

    private void closeIfStillIdle(long activityAt) {
        channel.eventLoop().execute(() -> {
            if (closed.get() || !channel.isActive()) {
                return;
            }
            if (lastActivityMillis != activityAt) {
                scheduleIdleCheck(Math.max(1, idleTimeoutMillis - (clock.getAsLong() - lastActivityMillis)));
                return;
            }
            close(4000, "Heartbeat timeout");
        });
    }

    private record QueuedFrame(TextWebSocketFrame frame, int bytes) {
    }
}
