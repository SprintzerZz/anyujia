package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalapi.domain.dto.LegalAnswerReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.LegalAnswerVO;
import com.anyujia.anyujialegalapi.domain.vo.LegalCitationVO;
import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import com.anyujia.anyujialegalservice.domain.model.LegalAnswerGeneration;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LegalAnswerService {
    private static final String DISCLAIMER = "本回答仅提供一般法律信息和风险提示，不替代律师针对具体事实提供的法律意见。";
    private static final List<String> RENTAL_SCOPE_TERMS = List.of(
            "租赁", "租房", "出租", "承租", "房东", "租客", "租金", "押金", "房屋", "住房",
            "维修", "转租", "退租", "腾退", "租约", "租期", "租多久", "房产证", "不动产权");

    private static final List<String> NON_RENTAL_DOMAIN_TERMS = List.of(
            "\u533b\u9662", "\u624b\u672f", "\u533b\u7597", "\u8bef\u5de5\u8d39",
            "\u4ea4\u901a\u4e8b\u6545", "\u52b3\u52a8", "\u5de5\u8d44", "\u79bb\u5a5a",
            "\u501f\u6b3e", "\u5211\u4e8b");
    private static final List<String> RENTAL_FACT_TERMS = List.of(
            "\u623f\u4e1c", "\u79df\u5ba2", "\u627f\u79df\u4eba", "\u51fa\u79df\u4eba",
            "\u51fa\u79df\u5c4b", "\u79df\u91d1", "\u623f\u79df", "\u62bc\u91d1",
            "\u9000\u79df", "\u7ef4\u4fee", "\u8f6c\u79df", "\u79df\u671f",
            "\u5408\u540c", "\u623f\u4ea7\u8bc1", "\u6709\u6743\u51fa\u79df",
            "\u51fa\u79df\u6743", "\u79df\u8d41\u4f4f\u623f");

    private final LegalKnowledgeRetriever retriever;
    private final LegalAnswerGenerator answerGenerator;

    public LegalAnswerVO answer(LegalAnswerReqDTO request) {
        long started = System.nanoTime();
        if (!isRentalQuestion(request.getQuery())) {
            return LegalAnswerVO.builder()
                    .answer("该问题不属于当前住房租赁法律知识库的覆盖范围，无法依据本知识库作答。请改用对应领域的法律资料或咨询专业人士。")
                    .citations(List.of())
                    .mode("OUT_OF_SCOPE")
                    .aiGenerated(false)
                    .degraded(false)
                    .retrievalMillis(0L)
                    .generationMillis(0L)
                    .elapsedMillis(elapsed(started))
                    .disclaimer(DISCLAIMER)
                    .build();
        }
        LocalDate asOfDate = request.getAsOfDate() == null || request.getAsOfDate().isBlank()
                ? LocalDate.now() : LocalDate.parse(request.getAsOfDate());
        long retrievalStarted = System.nanoTime();
        List<LawPassage> passages = retriever.retrieve(request.getQuery(), request.getRegionCode(), asOfDate,
                request.getLimit() == null ? 5 : request.getLimit());
        long retrievalMillis = elapsed(retrievalStarted);
        long generationStarted = System.nanoTime();
        LegalAnswerGeneration generation = answerGenerator.generate(request.getQuery(), passages);
        long generationMillis = elapsed(generationStarted);
        LinkedHashSet<Integer> allowedIndexes = new LinkedHashSet<>();
        if (generation.citationIndexes() != null) {
            generation.citationIndexes().stream()
                    .filter(index -> index != null && index >= 1 && index <= passages.size())
                    .forEach(allowedIndexes::add);
        }
        List<LegalCitationVO> citations = allowedIndexes.stream()
                .map(index -> citation(index, passages.get(index - 1)))
                .toList();
        return LegalAnswerVO.builder()
                .answer(generation.answer())
                .citations(citations)
                .mode(generation.mode())
                .aiGenerated(generation.aiGenerated())
                .degraded(generation.degraded())
                .retrievalMillis(retrievalMillis)
                .generationMillis(generationMillis)
                .elapsedMillis(elapsed(started))
                .disclaimer(DISCLAIMER)
                .build();
    }

    private boolean isRentalQuestion(String query) {
        if (query != null && (query.contains("\u533b\u9662") || query.contains("\u624b\u672f")
                || query.contains("\u533b\u7597"))) {
            return false;
        }
        if (query == null || RENTAL_SCOPE_TERMS.stream().noneMatch(query::contains)) {
            return false;
        }
        boolean hasNonRentalDomain = NON_RENTAL_DOMAIN_TERMS.stream().anyMatch(query::contains);
        if (!hasNonRentalDomain) {
            return true;
        }
        return RENTAL_FACT_TERMS.stream().anyMatch(query::contains);
    }

    private LegalCitationVO citation(int index, LawPassage passage) {
        return LegalCitationVO.builder()
                .index(index)
                .chunkId(passage.chunkId())
                .sourceId(passage.sourceId())
                .title(passage.title())
                .articleNumber(passage.articleNumber())
                .quote(passage.text())
                .sourceUrl(passage.sourceUrl())
                .build();
    }

    private long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }
}
