package com.anyujia.anyujiagateway.filter;

import com.anyujia.anyujiacommondomain.constants.AuthenticationConstants;
import com.anyujia.anyujiacommondomain.domain.WebSocketTicketPrincipal;
import com.anyujia.anyujiacommonredis.service.RedisService;
import com.anyujia.anyujiagateway.config.IgnoreWhiteProperties;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
class AuthFilterTest {

    @Test
    void websocketTicketIsConsumedAndTrustedHeadersReplaceClientValues() {
        StubRedisService redisService = new StubRedisService();
        IgnoreWhiteProperties whiteProperties = new IgnoreWhiteProperties();
        whiteProperties.setWhites(List.of());
        AuthFilter filter = new AuthFilter(whiteProperties, redisService);
        String ticket = "single-use-ticket";
        redisService.principal = new WebSocketTicketPrincipal("2", "landlord", "app", "2");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/chat/websocket?ticket=" + ticket)
                        .header("userId", "999")
                        .header("userFrom", "forged"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("userId")).isEqualTo("2");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("userFrom")).isEqualTo("app");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("userType")).isEqualTo("2");
        assertThat(redisService.consumedKey)
                .isEqualTo(AuthenticationConstants.WEBSOCKET_TICKET_PREFIX + ticket);
    }

    private static final class StubRedisService extends RedisService {
        private WebSocketTicketPrincipal principal;
        private String consumedKey;

        private StubRedisService() {
            super(null);
        }

        @Override
        public <T> T getAndDeleteCacheObject(String key, Class<T> clazz) {
            consumedKey = key;
            return clazz.cast(principal);
        }
    }
}
