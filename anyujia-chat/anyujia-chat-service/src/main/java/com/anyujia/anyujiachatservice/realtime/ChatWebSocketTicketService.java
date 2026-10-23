package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiachatapi.domain.vo.WebSocketTicketVO;
import com.anyujia.anyujiacommondomain.constants.AuthenticationConstants;
import com.anyujia.anyujiacommondomain.domain.WebSocketTicketPrincipal;
import com.anyujia.anyujiacommonredis.service.RedisService;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ChatWebSocketTicketService {
    private final RedisService redisService;

    public WebSocketTicketVO issue(Long userId, String userName, String userFrom, String userType) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        WebSocketTicketPrincipal principal = new WebSocketTicketPrincipal(
                String.valueOf(userId), userName, userFrom, userType);
        redisService.setCacheObject(
                AuthenticationConstants.WEBSOCKET_TICKET_PREFIX + ticket,
                principal,
                AuthenticationConstants.WEBSOCKET_TICKET_TTL_SECONDS,
                TimeUnit.SECONDS);
        return new WebSocketTicketVO(ticket, AuthenticationConstants.WEBSOCKET_TICKET_TTL_SECONDS);
    }

    /**
     * Atomically consumes a ticket and validates the identity stored in it. The returned identity
     * is the only source of truth for a WebSocket connection; client-supplied user headers are not
     * trusted.
     */
    public WebSocketTicketPrincipal consume(String ticket) {
        if (!StringUtils.hasText(ticket) || ticket.length() > 128) {
            return null;
        }
        WebSocketTicketPrincipal principal = redisService.getAndDeleteCacheObject(
                AuthenticationConstants.WEBSOCKET_TICKET_PREFIX + ticket,
                WebSocketTicketPrincipal.class);
        if (principal == null || !StringUtils.hasText(principal.userId())
                || !StringUtils.hasText(principal.userFrom())
                || !"app".equals(principal.userFrom())) {
            return null;
        }
        try {
            if (Long.parseLong(principal.userId()) <= 0) {
                return null;
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return principal;
    }
}
