package com.anyujia.anyujiachatservice.realtime.netty;

import com.anyujia.anyujiachatservice.realtime.ChatConnectionRegistry;
import com.anyujia.anyujiachatservice.realtime.ChatFrameProcessor;
import com.anyujia.anyujiachatservice.realtime.ChatWebSocketTicketService;
import com.anyujia.anyujiacommondomain.domain.WebSocketTicketPrincipal;
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
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final ChatWebSocketTicketService ticketService;
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
    private final Set<String> allowedOrigins;
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
            ChatWebSocketTicketService ticketService,
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
            @Value("${chat.netty.write-buffer-high-water-mark:65536}") int writeHighWaterMark,
            @Value("${chat.websocket.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
            String configuredAllowedOrigins) {
        this.registry = registry;
        this.frameProcessor = frameProcessor;
        this.ticketService = ticketService;
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
        this.allowedOrigins = Arrays.stream(configuredAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        if (this.allowedOrigins.isEmpty() || this.allowedOrigins.contains("*")) {
            throw new IllegalArgumentException("chat.websocket.allowed-origins must be explicit and non-wildcard");
        }
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
                                    .addLast(new WebSocketFrameAggregator(maxFrameBytes))
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
            String ticket = new QueryStringDecoder(request.uri()).parameters()
                    .getOrDefault("ticket", java.util.List.of())
                    .stream().findFirst().orElse(null);
            String origin = request.headers().get(HttpHeaderNames.ORIGIN);
            if (origin != null && !allowedOrigins.contains(origin)) {
                rejectHandshake(context, message, HttpResponseStatus.FORBIDDEN);
                return;
            }
            if (ticket == null) {
                rejectHandshake(context, message, HttpResponseStatus.UNAUTHORIZED);
                return;
            }
            try {
                businessExecutor.execute(() -> {
                    WebSocketTicketPrincipal principal = ticketService.consume(ticket);
                    context.executor().execute(() -> {
                        if (principal == null) {
                            rejectHandshake(context, message, HttpResponseStatus.UNAUTHORIZED);
                            return;
                        }
                        context.channel().attr(USER_ID).set(Long.valueOf(principal.userId()));
                        context.fireChannelRead(message);
                    });
                });
            } catch (RejectedExecutionException ex) {
                rejectHandshake(context, message, HttpResponseStatus.SERVICE_UNAVAILABLE);
            }
        }

        private void rejectHandshake(ChannelHandlerContext context, Object message,
                                     HttpResponseStatus status) {
            ReferenceCountUtil.release(message);
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status, Unpooled.EMPTY_BUFFER);
            HttpUtil.setContentLength(response, 0);
            response.headers().set(HttpHeaderNames.CONNECTION, "close");
            context.writeAndFlush(response).addListener(ignored -> context.close());
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
