package com.anyujia.anyujialegalservice.service;

import java.util.List;
import java.util.Optional;

public interface LegalEmbeddingClient {
    Optional<double[]> embed(String text);

    default List<Optional<double[]>> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
