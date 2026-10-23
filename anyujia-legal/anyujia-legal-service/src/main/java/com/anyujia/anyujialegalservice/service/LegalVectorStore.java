package com.anyujia.anyujialegalservice.service;

import java.util.Collection;
import java.util.List;

public interface LegalVectorStore {
    List<VectorHit> search(double[] queryVector, int topK, Collection<Long> sourceIds);

    void upsert(long chunkId, long sourceId, String articleNumber, String title,
                String sourceUrl, String region, double[] vector);

    void deleteBySourceId(long sourceId);

    boolean isAvailable();

    record VectorHit(long chunkId, double score) {
    }
}
