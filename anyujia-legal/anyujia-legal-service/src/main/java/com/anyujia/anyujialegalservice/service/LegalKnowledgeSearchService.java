package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalapi.domain.dto.LegalSearchReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.LegalPassageVO;
import com.anyujia.anyujialegalapi.domain.vo.LegalSearchVO;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LegalKnowledgeSearchService {
    private final LegalKnowledgeRetriever retriever;

    public LegalSearchVO search(LegalSearchReqDTO request) {
        long started = System.nanoTime();
        LocalDate asOfDate = request.getAsOfDate() == null || request.getAsOfDate().isBlank()
                ? LocalDate.now() : LocalDate.parse(request.getAsOfDate());
        var passages = retriever.retrieve(request.getQuery(), request.getRegionCode(), asOfDate,
                        request.getLimit() == null ? 5 : request.getLimit()).stream()
                .map(passage -> LegalPassageVO.builder()
                        .chunkId(passage.chunkId())
                        .sourceId(passage.sourceId())
                        .title(passage.title())
                        .articleNumber(passage.articleNumber())
                        .text(passage.text())
                        .sourceUrl(passage.sourceUrl())
                        .score(passage.score())
                        .build())
                .toList();
        return LegalSearchVO.builder()
                .elapsedMillis((System.nanoTime() - started) / 1_000_000L)
                .passages(passages)
                .build();
    }
}
