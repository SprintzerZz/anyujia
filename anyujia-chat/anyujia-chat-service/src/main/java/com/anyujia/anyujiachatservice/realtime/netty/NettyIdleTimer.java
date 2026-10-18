package com.anyujia.anyujiachatservice.realtime.netty;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

final class NettyIdleTimer implements AutoCloseable {
    private final HashedWheelTimer timer;

    NettyIdleTimer(long tickMillis) {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "chat-netty-idle-wheel");
            thread.setDaemon(true);
            return thread;
        };
        timer = new HashedWheelTimer(threadFactory, tickMillis, TimeUnit.MILLISECONDS, 512);
    }

    Timeout schedule(Runnable task, long delayMillis) {
        return timer.newTimeout(ignored -> task.run(), Math.max(1, delayMillis), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        timer.stop();
    }
}
