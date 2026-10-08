package com.anyujia.anyujialegalservice.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegalEmbeddingBackfillCliTest {
    @Test
    void batchEmbeddingRestoresVectorsToInputOrder() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/embeddings", exchange -> {
            byte[] response = ("{\"data\":["
                    + "{\"index\":1,\"embedding\":[2.0,2.1]},"
                    + "{\"index\":0,\"embedding\":[1.0,1.1]}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

            List<double[]> result = LegalEmbeddingBackfillCli.embedBatch(
                    baseUrl, "test-key", "test-model", 2, List.of("first", "second"));

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsExactly(1.0, 1.1);
            assertThat(result.get(1)).containsExactly(2.0, 2.1);
        } finally {
            server.stop(0);
        }
    }
}
