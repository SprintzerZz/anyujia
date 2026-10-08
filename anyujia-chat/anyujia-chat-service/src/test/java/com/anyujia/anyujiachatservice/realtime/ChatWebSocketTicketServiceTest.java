package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiachatapi.domain.vo.WebSocketTicketVO;
import com.anyujia.anyujiacommondomain.constants.AuthenticationConstants;
import com.anyujia.anyujiacommondomain.domain.WebSocketTicketPrincipal;
import com.anyujia.anyujiacommonredis.service.RedisService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatWebSocketTicketServiceTest {

    @Test
    void issuedTicketStoresShortLivedPrincipal() {
        StubRedisService redisService = new StubRedisService();
        ChatWebSocketTicketService service = new ChatWebSocketTicketService(redisService);

        WebSocketTicketVO result = service.issue(2L, "landlord", "app", "2");

        assertThat(result.getTicket()).hasSize(32);
        assertThat(result.getExpiresInSeconds()).isEqualTo(60L);
        assertThat(redisService.key).isEqualTo(AuthenticationConstants.WEBSOCKET_TICKET_PREFIX + result.getTicket());
        assertThat(redisService.principal.userId()).isEqualTo("2");
        assertThat(redisService.principal.userFrom()).isEqualTo("app");
        assertThat(redisService.timeout).isEqualTo(60L);
        assertThat(redisService.timeUnit).isEqualTo(TimeUnit.SECONDS);
    }

    private static final class StubRedisService extends RedisService {
        private String key;
        private WebSocketTicketPrincipal principal;
        private Long timeout;
        private TimeUnit timeUnit;

        private StubRedisService() {
            super(null);
        }

        @Override
        public <T> void setCacheObject(String key, T value, Long timeout, TimeUnit timeUnit) {
            this.key = key;
            this.principal = (WebSocketTicketPrincipal) value;
            this.timeout = timeout;
            this.timeUnit = timeUnit;
        }
    }
}
