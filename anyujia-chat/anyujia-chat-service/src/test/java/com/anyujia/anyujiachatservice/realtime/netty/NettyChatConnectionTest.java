package com.anyujia.anyujiachatservice.realtime.netty;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
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
}
