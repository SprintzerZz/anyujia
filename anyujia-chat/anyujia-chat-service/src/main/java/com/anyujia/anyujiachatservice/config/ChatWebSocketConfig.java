package com.anyujia.anyujiachatservice.config;

import com.anyujia.anyujiachatservice.realtime.ChatWebSocketHandler;
import com.anyujia.anyujiachatservice.realtime.ChatWebSocketHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ChatWebSocketConfig implements WebSocketConfigurer {
    private final ChatWebSocketHandler handler;
    private final ChatWebSocketHandshakeInterceptor handshakeInterceptor;
    private final String allowedOrigins;
    private final boolean nettyEnabled;

    public ChatWebSocketConfig(
            ChatWebSocketHandler handler,
            ChatWebSocketHandshakeInterceptor handshakeInterceptor,
            @Value("${chat.websocket.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
            String allowedOrigins,
            @Value("${chat.netty.enabled:false}") boolean nettyEnabled) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.allowedOrigins = allowedOrigins;
        this.nettyEnabled = nettyEnabled;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        if (nettyEnabled) {
            // Netty owns /chat/websocket while enabled; keep this configuration active only
            // because the shared Spring WebSocket send executor is still used by its beans/tests.
            return;
        }
        var origins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (origins.isEmpty() || origins.stream().anyMatch("*"::equals)) {
            throw new IllegalStateException("chat.websocket.allowed-origins must be an explicit non-wildcard list");
        }
        registry.addHandler(handler, "/chat/websocket")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(origins.toArray(String[]::new));
    }

    @Bean(name = "chatWebSocketSendExecutor")
    public static ThreadPoolTaskExecutor chatWebSocketSendExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("chat-ws-send-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(1024);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
