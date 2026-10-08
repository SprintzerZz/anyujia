package com.anyujia.anyujialegalservice.domain.model;

public record LawPassage(Long chunkId, Long sourceId, String title, String articleNumber,
                         String text, String sourceUrl, double score) {
}
