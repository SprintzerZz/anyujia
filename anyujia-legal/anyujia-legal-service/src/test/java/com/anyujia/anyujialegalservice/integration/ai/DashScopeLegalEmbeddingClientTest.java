package com.anyujia.anyujialegalservice.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.redisson.api.RedissonClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DashScopeLegalEmbeddingClientTest {
    @Test
    void batchesUniqueTextsAndServesRepeatedQueriesFromLocalCache() {
        LegalAiProperties properties = properties();
        LegalEmbeddingCache cache = cache(properties);
        DashScopeCallGuard guard = new DashScopeCallGuard(properties);
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(properties.getBaseUrl() + "/embeddings"))
                .andExpect(content().json("""
                        {"model":"text-embedding-v4","input":["押金","进房"],"dimensions":1024}
                        """))
                .andRespond(withSuccess("""
                        {"data":[
                          {"index":0,"embedding":[1.0,0.0]},
                          {"index":1,"embedding":[0.0,1.0]}
                        ]}
                        """, MediaType.APPLICATION_JSON));
        DashScopeLegalEmbeddingClient client = new DashScopeLegalEmbeddingClient(
                properties, new ObjectMapper(), cache, guard, builder.build());

        var first = client.embedBatch(List.of("押金", "进房", "押金"));
        var second = client.embedBatch(List.of("押金", "进房", "押金"));

        assertThat(first).allMatch(java.util.Optional::isPresent);
        assertThat(first.get(0).orElseThrow()).containsExactly(1D, 0D);
        assertThat(first.get(1).orElseThrow()).containsExactly(0D, 1D);
        assertThat(first.get(2).orElseThrow()).containsExactly(1D, 0D);
        assertThat(second).allMatch(java.util.Optional::isPresent);
        server.verify();
    }

    @Test
    void opensCooldownAfterFailureAndReturnsEmptyVectorsForLexicalFallback() {
        LegalAiProperties properties = properties();
        properties.setFailureThreshold(1);
        LegalEmbeddingCache cache = cache(properties);
        DashScopeCallGuard guard = new DashScopeCallGuard(properties);
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(properties.getBaseUrl() + "/embeddings"))
                .andRespond(withServerError());
        DashScopeLegalEmbeddingClient client = new DashScopeLegalEmbeddingClient(
                properties, new ObjectMapper(), cache, guard, builder.build());

        var failed = client.embedBatch(List.of("押金", "进房"));
        var blocked = client.embedBatch(List.of("维修"));

        assertThat(failed).allMatch(java.util.Optional::isEmpty);
        assertThat(blocked).allMatch(java.util.Optional::isEmpty);
        assertThat(guard.allowRequest(DashScopeCallGuard.EMBEDDING)).isFalse();
        assertThat(guard.allowRequest(DashScopeCallGuard.CHAT)).isTrue();
        server.verify();
    }

    private LegalAiProperties properties() {
        LegalAiProperties properties = new LegalAiProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://dashscope.test/v1");
        return properties;
    }

    private LegalEmbeddingCache cache(LegalAiProperties properties) {
        return new LegalEmbeddingCache(properties, (RedissonClient) null, new ObjectMapper());
    }
}
