package com.anyujia.anyujialegalservice.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class DashScopeLegalAnswerGeneratorTest {
    @Test
    void fallsBackWhenCredentialIsMissing() {
        LegalAiProperties properties = new LegalAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("");
        DashScopeLegalAnswerGenerator generator = new DashScopeLegalAnswerGenerator(
                properties, new ObjectMapper(), RestClient.builder(), new DashScopeCallGuard(properties));

        var result = generator.generate("押金", List.of(new LawPassage(
                1L, 2L, "住房租赁条例", "第十条", "不得无正当理由扣减押金。", "https://gov.cn/law", 0.9)));

        assertThat(result.mode()).isEqualTo("EXTRACTIVE_FALLBACK");
        assertThat(result.answer()).contains("不得无正当理由扣减押金");
        assertThat(result.aiGenerated()).isFalse();
    }

    @Test
    void rejectsUnsupportedModelCitationAndFallsBack() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            ObjectMapper mapper = new ObjectMapper();
            String modelContent = mapper.writeValueAsString(
                    Map.of("answer", "无依据结论", "citationIndexes", List.of(99)));
            byte[] response = mapper.writeValueAsBytes(Map.of("choices", List.of(
                    Map.of("message", Map.of("content", modelContent)))));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            LegalAiProperties properties = new LegalAiProperties();
            properties.setEnabled(true);
            properties.setApiKey("test-key");
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            DashScopeLegalAnswerGenerator generator = new DashScopeLegalAnswerGenerator(
                    properties, new ObjectMapper(), RestClient.builder(), new DashScopeCallGuard(properties));

            var result = generator.generate("押金", List.of(new LawPassage(
                    1L, 2L, "住房租赁条例", "第十条", "不得无正当理由扣减押金。", "https://gov.cn/law", 0.9)));

            assertThat(result.mode()).isEqualTo("EXTRACTIVE_FALLBACK");
            assertThat(result.citationIndexes()).containsExactly(1);
            assertThat(result.answer()).contains("不得无正当理由扣减押金").doesNotContain("无依据结论");
        } finally {
            server.stop(0);
        }
    }
}
