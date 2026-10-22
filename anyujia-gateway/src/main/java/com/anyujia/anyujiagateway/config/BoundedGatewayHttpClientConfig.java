package com.anyujia.anyujiagateway.config;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.cloud.gateway.config.HttpClientFactory;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.config.HttpClientSslConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import static org.springframework.cloud.gateway.config.HttpClientProperties.Pool.PoolType.DISABLED;
import static org.springframework.cloud.gateway.config.HttpClientProperties.Pool.PoolType.FIXED;

/**
 * 为 Gateway 下游 HTTP 客户端增加有界的 pending acquire 队列。
 *
 * <p>Spring Cloud Gateway 原生 fixed pool 将 pendingAcquireMaxCount 固定为 -1，
 * 下游变慢时会把请求持续堆积在连接池等待队列中。本配置保留官方 HttpClientFactory
 * 的全部行为，仅替换连接池构造逻辑。</p>
 */
@Configuration
public class BoundedGatewayHttpClientConfig {

    @Bean
    public HttpClientFactory boundedGatewayHttpClientFactory(
            HttpClientProperties properties,
            ServerProperties serverProperties,
            List<HttpClientCustomizer> customizers,
            HttpClientSslConfigurer sslConfigurer,
            @Value("${spring.cloud.gateway.httpclient.pool.pending-acquire-max-count:256}") int pendingAcquireMaxCount) {
        return new BoundedHttpClientFactory(
                properties,
                serverProperties,
                sslConfigurer,
                customizers,
                Math.max(0, pendingAcquireMaxCount));
    }

    private static final class BoundedHttpClientFactory extends HttpClientFactory {

        private final int pendingAcquireMaxCount;

        private BoundedHttpClientFactory(
                HttpClientProperties properties,
                ServerProperties serverProperties,
                HttpClientSslConfigurer sslConfigurer,
                List<HttpClientCustomizer> customizers,
                int pendingAcquireMaxCount) {
            super(properties, serverProperties, sslConfigurer, customizers);
            this.pendingAcquireMaxCount = pendingAcquireMaxCount;
        }

        @Override
        protected ConnectionProvider buildConnectionProvider(HttpClientProperties properties) {
            HttpClientProperties.Pool pool = properties.getPool();
            if (pool.getType() == DISABLED) {
                return ConnectionProvider.newConnection();
            }

            ConnectionProvider.Builder builder = ConnectionProvider.builder(pool.getName())
                    .pendingAcquireMaxCount(pendingAcquireMaxCount);
            if (pool.getType() == FIXED) {
                builder.maxConnections(pool.getMaxConnections())
                        .pendingAcquireTimeout(Duration.ofMillis(pool.getAcquireTimeout()));
            } else {
                builder.maxConnections(Integer.MAX_VALUE)
                        .pendingAcquireTimeout(Duration.ZERO);
            }
            if (pool.getMaxIdleTime() != null) {
                builder.maxIdleTime(pool.getMaxIdleTime());
            }
            if (pool.getMaxLifeTime() != null) {
                builder.maxLifeTime(pool.getMaxLifeTime());
            }
            builder.evictInBackground(pool.getEvictionInterval());
            builder.metrics(pool.isMetrics());
            return builder.build();
        }
    }
}
