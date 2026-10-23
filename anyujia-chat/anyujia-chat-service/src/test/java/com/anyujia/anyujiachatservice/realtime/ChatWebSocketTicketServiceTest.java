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

    @Test
    void consumeReadsAndDeletesTicketIdentity() {
        StubRedisService redisService = new StubRedisService();
        redisService.principal = new WebSocketTicketPrincipal("2", "landlord", "app", "2");
        ChatWebSocketTicketService service = new ChatWebSocketTicketService(redisService);

        WebSocketTicketPrincipal result = service.consume("ticket-1");

        assertThat(result).isEqualTo(redisService.principal);
        assertThat(redisService.consumedKey)
                .isEqualTo(AuthenticationConstants.WEBSOCKET_TICKET_PREFIX + "ticket-1");
    }

    @Test
    void consumeRejectsNonAppIdentity() {
        StubRedisService redisService = new StubRedisService();
        redisService.principal = new WebSocketTicketPrincipal("2", "admin", "sys", "1");
        ChatWebSocketTicketService service = new ChatWebSocketTicketService(redisService);

        assertThat(service.consume("ticket-1")).isNull();
    }

    private static final class StubRedisService extends RedisService {
        private String key;
        private WebSocketTicketPrincipal principal;
        private String consumedKey;
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

        @Override
        public <T> T getAndDeleteCacheObject(String key, Class<T> clazz) {
            this.consumedKey = key;
            return principal == null ? null : clazz.cast(principal);
        }
    }
}
