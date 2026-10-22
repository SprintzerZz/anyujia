package com.anyujia.anyujiagateway.filter;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

/**
 * Observes the time spent after Gateway routing starts until the downstream
 * response completes. Tags intentionally stay limited to the route id.
 */
@Component
public class GatewayForwardMetricsFilter implements GlobalFilter, Ordered {
    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeRequests = new AtomicInteger();

    public GatewayForwardMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("gateway.forward.active", activeRequests, AtomicInteger::get)
                .description("Gateway requests currently waiting for downstream response")
                .register(meterRegistry);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long started = System.nanoTime();
        activeRequests.incrementAndGet();
        return chain.filter(exchange)
                .doFinally(signal -> {
                    activeRequests.decrementAndGet();
                    Timer.builder("gateway.forward")
                            .description("Gateway downstream forwarding duration")
                            .tag("route", routeId(exchange))
                            .publishPercentiles(0.95, 0.99)
                            .register(meterRegistry)
                            .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
                });
    }

    private String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route == null || route.getId() == null ? "unknown" : route.getId();
    }
}
