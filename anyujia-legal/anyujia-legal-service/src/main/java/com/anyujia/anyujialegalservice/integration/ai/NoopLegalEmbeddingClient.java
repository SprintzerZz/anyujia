package com.anyujia.anyujialegalservice.integration.ai;

import com.anyujia.anyujialegalservice.service.LegalEmbeddingClient;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "legal.ai.embedding", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopLegalEmbeddingClient implements LegalEmbeddingClient {
    @Override
    public Optional<double[]> embed(String text) {
        return Optional.empty();
    }
}
