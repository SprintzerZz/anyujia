package com.anyujia.anyujiachatservice.realtime.netty;

import com.anyujia.anyujiachatservice.realtime.ChatConnectionRegistry;
import com.anyujia.anyujiachatservice.realtime.ChatFrameProcessor;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "chat.netty", name = "enabled", havingValue = "true")
@Slf4j
public class NettyChatServer implements SmartLifecycle {
    private static final String WEBSOCKET_PATH = "/chat/websocket";
    private static final AttributeKey<Long> USER_ID = AttributeKey.valueOf("chat.userId");

    private final ChatConnectionRegistry registry;
    private final ChatFrameProcessor frameProcessor;
    private final int port;
    private final String bindHost;
    private final int workerThreads;
    private final int maxFrameBytes;
    private final int maxQueuedMessages;
    private final long maxQueuedBytes;
    private final int inboundQueueLimit;
    private final long idleTimeoutMillis;
    private final long timerTickMillis;
    private final int writeLowWaterMark;
    private final int writeHighWaterMark;
    private final ThreadPoolExecutor businessExecutor;
    private final ChannelGroup clientChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private volatile boolean running;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private NettyIdleTimer idleTimer;
    private Channel serverChannel;

    public NettyChatServer(
            ChatConnectionRegistry registry,
            ChatFrameProcessor frameProcessor,
            @Value("${chat.netty.bind-host:127.0.0.1}") String bindHost,
            @Value("${chat.netty.port:18084}") int port,
            @Value("${chat.netty.worker-threads:0}") int workerThreads,
            @Value("${chat.netty.business-threads:16}") int businessThreads,
            @Value("${chat.netty.business-queue-capacity:1024}") int businessQueueCapacity,
            @Value("${chat.netty.max-frame-bytes:65536}") int maxFrameBytes,
            @Value("${chat.netty.outbound.max-queued-messages:256}") int maxQueuedMessages,
            @Value("${chat.netty.outbound.max-queued-bytes:2097152}") long maxQueuedBytes,
            @Value("${chat.netty.inbound.max-queued-messages:64}") int inboundQueueLimit,
            @Value("${chat.websocket.idle-timeout-seconds:150}") long idleTimeoutSeconds,
            @Value("${chat.netty.timer-tick-millis:1000}") long timerTickMillis,
            @Value("${chat.netty.write-buffer-low-water-mark:32768}") int writeLowWaterMark,
            @Value("${chat.netty.write-buffer-high-water-mark:65536}") int writeHighWaterMark) {
        this.registry = registry;
        this.frameProcessor = frameProcessor;
        this.bindHost = bindHost;
        this.port = port;
        this.workerThreads = workerThreads;
        this.maxFrameBytes = maxFrameBytes;
        this.maxQueuedMessages = maxQueuedMessages;
        this.maxQueuedBytes = maxQueuedBytes;
        this.inboundQueueLimit = inboundQueueLimit;
        this.idleTimeoutMillis = TimeUnit.SECONDS.toMillis(idleTimeoutSeconds);
        this.timerTickMillis = timerTickMillis;
        this.writeLowWaterMark = writeLowWaterMark;
        this.writeHighWaterMark = writeHighWaterMark;
        AtomicInteger threadNumber = new AtomicInteger();
        this.businessExecutor = new ThreadPoolExecutor(
                businessThreads, businessThreads, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(businessQueueCapacity),
                runnable -> new Thread(runnable, "chat-netty-business-" + threadNumber.incrementAndGet()),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = workerThreads > 0 ? new NioEventLoopGroup(workerThreads) : new NioEventLoopGroup();
        idleTimer = new NettyIdleTimer(timerTickMillis);
        WebSocketServerProtocolConfig websocketConfig = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath(WEBSOCKET_PATH)
                .checkStartsWith(true)
                .allowExtensions(true)
                .maxFramePayloadLength(maxFrameBytes)
                .build();
        try {
            serverChannel = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                            new WriteBufferWaterMark(writeLowWaterMark, writeHighWaterMark))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            clientChannels.add(channel);
                            channel.pipeline()
                                    .addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(maxFrameBytes))
                                    .addLast(new GatewayAuthenticationHandler())
                                    .addLast(new WebSocketServerProtocolHandler(websocketConfig))
                                    .addLast(new RealtimeFrameHandler());
                        }
                    })
                    .bind(bindHost, port).syncUninterruptibly().channel();
            running = true;
            log.info("Netty chat WebSocket server listening on {}:{}", bindHost, port);
        } catch (RuntimeException ex) {
            stop();
            throw ex;
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
            serverChannel = null;
        }
        clientChannels.close().awaitUninterruptibly();
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
            workerGroup = null;
        }
        if (idleTimer != null) {
            idleTimer.close();
            idleTimer = null;
        }
        businessExecutor.shutdown();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    private final class GatewayAuthenticationHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext context, Object message) {
            if (!(message instanceof FullHttpRequest request)
                    || !request.uri().startsWith(WEBSOCKET_PATH)) {
                context.fireChannelRead(message);
                return;
            }
            String userId = request.headers().get("userId");
            String userFrom = request.headers().get("userFrom");
            try {
                if (!"app".equals(userFrom)) {
                    throw new IllegalArgumentException("invalid user source");
                }
                context.channel().attr(USER_ID).set(Long.valueOf(userId));
                context.fireChannelRead(message);
            } catch (RuntimeException ex) {
                ReferenceCountUtil.release(message);
                DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, Unpooled.EMPTY_BUFFER);
                HttpUtil.setContentLength(response, 0);
                response.headers().set(HttpHeaderNames.CONNECTION, "close");
                context.writeAndFlush(response).addListener(ignored -> context.close());
            }
        }
    }

    private final class RealtimeFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
        private final Deque<String> inbound = new ArrayDeque<>();
        private NettyChatConnection connection;
        private boolean businessBusy;
        private boolean inactive;
        private boolean offlineScheduled;
        private boolean shouldAnnounceOffline;
        private CompletableFuture<Void> onlineFuture = CompletableFuture.completedFuture(null);

        @Override
        public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
            if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                Long userId = context.channel().attr(USER_ID).get();
                if (userId == null) {
                    context.close();
                    return;
                }
                connection = new NettyChatConnection(userId, context.channel(), idleTimer,
                        idleTimeoutMillis, maxQueuedMessages, maxQueuedBytes);
                registry.addLocal(connection);
                try {
                    onlineFuture = CompletableFuture.runAsync(
                            () -> registry.announceOnline(connection), businessExecutor);
                } catch (RejectedExecutionException ex) {
                    connection.close(1013, "Business executor overloaded");
                }
            }
            super.userEventTriggered(context, event);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, WebSocketFrame frame) {
            if (!(frame instanceof TextWebSocketFrame textFrame) || connection == null) {
                return;
            }
            connection.touch();
            if (inbound.size() >= inboundQueueLimit) {
                connection.close(1013, "Inbound queue overloaded");
                return;
            }
            inbound.addLast(textFrame.text());
            dispatchNext(context);
        }

        private void dispatchNext(ChannelHandlerContext context) {
            if (businessBusy || inbound.isEmpty() || connection == null) {
                return;
            }
            String payload = inbound.removeFirst();
            businessBusy = true;
            try {
                businessExecutor.execute(() -> {
                    try {
                        registry.announceHeartbeat(connection);
                        frameProcessor.process(connection.userId(), payload, connection::send);
                    } catch (RuntimeException ex) {
                        log.warn("Netty chat frame processing failed, connectionId={}", connection.id(), ex);
                        connection.close(1011, "Message processing failed");
                    } finally {
                        context.executor().execute(() -> {
                            businessBusy = false;
                            if (inactive && shouldAnnounceOffline) {
                                scheduleOffline();
                            } else {
                                dispatchNext(context);
                            }
                        });
                    }
                });
            } catch (RejectedExecutionException ex) {
                businessBusy = false;
                connection.close(1013, "Business executor overloaded");
            }
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext context) throws Exception {
            if (connection != null && context.channel().isWritable()) {
                connection.drain();
            }
            super.channelWritabilityChanged(context);
        }

        @Override
        public void channelInactive(ChannelHandlerContext context) throws Exception {
            if (connection != null) {
                inactive = true;
                inbound.clear();
                connection.discardQueuedFrames();
                shouldAnnounceOffline = registry.removeLocal(connection);
                if (shouldAnnounceOffline) {
                    if (!businessBusy) {
                        scheduleOffline();
                    }
                }
            }
            super.channelInactive(context);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            log.debug("Netty chat connection failed, channelId={}", context.channel().id(), cause);
            context.close();
        }

        private void scheduleOffline() {
            if (!shouldAnnounceOffline || offlineScheduled || connection == null) {
                return;
            }
            offlineScheduled = true;
            try {
                onlineFuture.handle((ignored, failure) -> null)
                        .thenRunAsync(() -> registry.announceOffline(connection), businessExecutor);
            } catch (RejectedExecutionException ex) {
                log.warn("Unable to publish Netty chat offline state, connectionId={}", connection.id(), ex);
            }
        }
    }
}
