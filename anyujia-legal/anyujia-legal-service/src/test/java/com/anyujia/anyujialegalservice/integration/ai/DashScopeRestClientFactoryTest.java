package com.anyujia.anyujialegalservice.integration.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class DashScopeRestClientFactoryTest {
    @Test
    void enforcesConfiguredReadTimeout() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(500L);
                byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            LegalAiProperties properties = new LegalAiProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setConnectTimeoutMillis(500);
            properties.setReadTimeoutMillis(100);
            RestClient client = DashScopeRestClientFactory.create(RestClient.builder(), properties);

            assertThatThrownBy(() -> client.get().uri("/slow").retrieve().body(String.class))
                    .isInstanceOf(ResourceAccessException.class);
        } finally {
            server.stop(0);
        }
    }
}
