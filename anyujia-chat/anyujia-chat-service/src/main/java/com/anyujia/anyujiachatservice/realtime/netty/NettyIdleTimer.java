package com.anyujia.anyujiachatservice.realtime.netty;

import io.netty.util.HashedWheelTimer;
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

    void schedule(Runnable task, long delayMillis) {
        timer.newTimeout(ignored -> task.run(), Math.max(1, delayMillis), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        timer.stop();
    }
}
