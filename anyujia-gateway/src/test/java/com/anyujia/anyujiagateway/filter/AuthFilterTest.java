package com.anyujia.anyujiagateway.filter;

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
    void websocketTicketIsForwardedForDownstreamAtomicConsumption() {
        StubRedisService redisService = new StubRedisService();
        IgnoreWhiteProperties whiteProperties = new IgnoreWhiteProperties();
        whiteProperties.setWhites(List.of());
        AuthFilter filter = new AuthFilter(whiteProperties, redisService);
        String ticket = "single-use-ticket";

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
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("userId")).isNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("userFrom")).isNull();
        assertThat(forwarded.get().getRequest().getURI().getQuery()).isEqualTo("ticket=" + ticket);
    }

    private static final class StubRedisService extends RedisService {
        private StubRedisService() {
            super(null);
        }
    }
}
