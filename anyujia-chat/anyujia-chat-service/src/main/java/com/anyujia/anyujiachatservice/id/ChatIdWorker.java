package com.anyujia.anyujiachatservice.id;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Bounded retry for the Snowflake generator during short host clock corrections. */
public final class ChatIdWorker {
    private static final long MAX_WAIT_NANOS = TimeUnit.SECONDS.toNanos(2);

    private ChatIdWorker() { }

    public static long nextId() {
        return retryOnClockRollback(IdWorker::getId);
    }

    static long retryOnClockRollback(LongSupplier generator) {
        long deadline = System.nanoTime() + MAX_WAIT_NANOS;
        while (true) {
            try {
                return generator.getAsLong();
            } catch (RuntimeException ex) {
                if (!clockRollback(ex) || System.nanoTime() >= deadline) {
                    throw ex;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for the clock to recover", interrupted);
                }
            }
        }
    }

    private static boolean clockRollback(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("Clock moved backwards")) {
                return true;
            }
        }
        return false;
    }
}
