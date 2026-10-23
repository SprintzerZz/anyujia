package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiacommondomain.domain.WebSocketTicketPrincipal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.util.StringUtils;

@Component
public class ChatWebSocketHandshakeInterceptor implements HandshakeInterceptor {
    private final ChatWebSocketTicketService ticketService;

    public ChatWebSocketHandshakeInterceptor(ChatWebSocketTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String ticket = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("ticket");
        WebSocketTicketPrincipal principal = ticketService.consume(ticket);
        if (principal == null || !StringUtils.hasText(principal.userId())) {
            return false;
        }
        try {
            attributes.put("userId", Long.valueOf(principal.userId()));
            attributes.put("userName", principal.userName());
            attributes.put("userFrom", principal.userFrom());
            attributes.put("userType", principal.userType());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
