package com.anyujia.anyujiachatservice.realtime.netty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

class WebSocketFrameAggregatorTest {
    @Test
    void aggregatesTextFragmentsIntoOneCompleteMessage() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameAggregator(32));
        try {
            assertThat(channel.writeInbound(new TextWebSocketFrame(false, 0, "hello "))).isFalse();
            assertThat(channel.writeInbound(new ContinuationWebSocketFrame(true, 0, "world"))).isTrue();

            TextWebSocketFrame frame = channel.readInbound();
            assertThat(frame.text()).isEqualTo("hello world");
            ReferenceCountUtil.release(frame);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void rejectsMessageLargerThanCompleteMessageLimit() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameAggregator(5));
        try {
            assertThatThrownBy(() -> {
                channel.writeInbound(new TextWebSocketFrame(false, 0, "hello"));
                channel.writeInbound(new ContinuationWebSocketFrame(true, 0, "!"));
            }).isInstanceOf(TooLongFrameException.class);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void rejectsDifferentDataTypeInsideFragmentedMessage() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameAggregator(32));
        channel.writeInbound(new TextWebSocketFrame(false, 0, "part"));
        channel.writeInbound(new BinaryWebSocketFrame(true, 0,
                io.netty.buffer.Unpooled.wrappedBuffer(new byte[] {1})));
        assertThatThrownBy(channel::finishAndReleaseAll)
                .isInstanceOf(PrematureChannelClosureException.class);
    }
}
