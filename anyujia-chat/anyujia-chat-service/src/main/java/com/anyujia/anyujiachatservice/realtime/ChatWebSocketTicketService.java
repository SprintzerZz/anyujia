package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiachatapi.domain.vo.WebSocketTicketVO;
import com.anyujia.anyujiacommondomain.constants.AuthenticationConstants;
import com.anyujia.anyujiacommondomain.domain.WebSocketTicketPrincipal;
import com.anyujia.anyujiacommonredis.service.RedisService;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
