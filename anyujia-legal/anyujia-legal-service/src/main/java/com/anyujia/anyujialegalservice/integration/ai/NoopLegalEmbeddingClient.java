package com.anyujia.anyujialegalservice.integration.ai;

import com.anyujia.anyujialegalservice.service.LegalEmbeddingClient;
import java.util.Optional;

public class NoopLegalEmbeddingClient implements LegalEmbeddingClient {
    @Override
    public Optional<double[]> embed(String text) {
        return Optional.empty();
    }
}
