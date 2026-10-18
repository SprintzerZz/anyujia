package com.anyujia.anyujiachatservice.realtime.netty;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class NettyChatConnectionTest {
    @Test
    void eventLoopDrainsOutboundFramesInOrder() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try (NettyIdleTimer timer = new NettyIdleTimer(10)) {
            NettyChatConnection connection = new NettyChatConnection(
                    1L, channel, timer, 10_000, 8, 1024);

            connection.send("first");
            connection.send("second");
            channel.runPendingTasks();

            TextWebSocketFrame first = channel.readOutbound();
            TextWebSocketFrame second = channel.readOutbound();
            assertThat(first.text()).isEqualTo("first");
            assertThat(second.text()).isEqualTo("second");
            ReferenceCountUtil.release(first);
            ReferenceCountUtil.release(second);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void queueOverflowClosesSlowConsumer() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try (NettyIdleTimer timer = new NettyIdleTimer(10)) {
            NettyChatConnection connection = new NettyChatConnection(
                    1L, channel, timer, 10_000, 1, 1024);

            connection.send("first");
            connection.send("second");
            channel.runPendingTasks();

            Object first = channel.readOutbound();
            Object second = channel.readOutbound();
            assertThat(first).isInstanceOf(TextWebSocketFrame.class);
            assertThat(second).isInstanceOf(CloseWebSocketFrame.class);
            assertThat(((CloseWebSocketFrame) second).statusCode()).isEqualTo(4008);
            ReferenceCountUtil.release(first);
            ReferenceCountUtil.release(second);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void closeIsIdempotentAndCancelsIdleTimeout() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        try (NettyIdleTimer timer = new NettyIdleTimer(5)) {
            NettyChatConnection connection = new NettyChatConnection(
                    1L, channel, timer, 30, 8, 1024);

            connection.close(4000, "timeout");
            connection.close(4001, "duplicate");
            channel.runPendingTasks();
            Thread.sleep(60);
            channel.runPendingTasks();

            Object close = channel.readOutbound();
            assertThat(close).isInstanceOf(CloseWebSocketFrame.class);
            assertThat(((CloseWebSocketFrame) close).statusCode()).isEqualTo(4000);
            assertThat((Object) channel.readOutbound()).isNull();
            ReferenceCountUtil.release(close);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void heartbeatAfterTimeoutDecisionDoesNotCloseRecoveredConnection() {
        EmbeddedChannel channel = new EmbeddedChannel();
        AtomicLong now = new AtomicLong(0);
        try (NettyIdleTimer timer = new NettyIdleTimer(10)) {
            NettyChatConnection connection = new NettyChatConnection(
                    1L, channel, timer, 100, 8, 1024, now::get);

            now.set(100);
            connection.checkIdle();
            now.set(101);
            connection.touch();
            channel.runPendingTasks();

            assertThat(connection.isOpen()).isTrue();
            assertThat((Object) channel.readOutbound()).isNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void concurrentTouchAndCloseEmitsAtMostOneCloseFrame() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        try (NettyIdleTimer timer = new NettyIdleTimer(1)) {
            NettyChatConnection connection = new NettyChatConnection(
                    1L, channel, timer, 10_000, 8, 1024);
            CountDownLatch start = new CountDownLatch(1);
            Thread toucher = new Thread(() -> awaitAndTouch(start, connection));
            Thread closer = new Thread(() -> awaitAndClose(start, connection));
            toucher.start();
            closer.start();
            start.countDown();
            toucher.join();
            closer.join();
            channel.runPendingTasks();

            int closeFrames = 0;
            Object outbound;
            while ((outbound = channel.readOutbound()) != null) {
                if (outbound instanceof CloseWebSocketFrame) {
                    closeFrames++;
                }
                ReferenceCountUtil.release(outbound);
            }
            assertThat(closeFrames).isLessThanOrEqualTo(1);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void manyShortLivedConnectionsCancelTheirOwnIdleTasks() {
        try (NettyIdleTimer timer = new NettyIdleTimer(1)) {
            for (int index = 0; index < 128; index++) {
                EmbeddedChannel channel = new EmbeddedChannel();
                NettyChatConnection connection = new NettyChatConnection(
                        (long) index, channel, timer, 5, 8, 1024);
                connection.close(1000, "short-lived");
                connection.close(1001, "duplicate");
                channel.runPendingTasks();
                Object outbound = channel.readOutbound();
                assertThat(outbound).isInstanceOf(CloseWebSocketFrame.class);
                assertThat((Object) channel.readOutbound()).isNull();
                ReferenceCountUtil.release(outbound);
                channel.finishAndReleaseAll();
            }
        }
    }

    private void awaitAndTouch(CountDownLatch start, NettyChatConnection connection) {
        await(start);
        connection.touch();
    }

    private void awaitAndClose(CountDownLatch start, NettyChatConnection connection) {
        await(start);
        connection.close(4000, "concurrent");
    }

    private void await(CountDownLatch start) {
        try {
            assertThat(start.await(1, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }
}
