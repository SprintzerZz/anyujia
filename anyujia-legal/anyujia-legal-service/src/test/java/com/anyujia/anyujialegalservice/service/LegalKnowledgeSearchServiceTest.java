package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.anyujia.anyujialegalapi.domain.dto.LegalSearchReqDTO;
import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegalKnowledgeSearchServiceTest {
    @Test
    void exposesRankedPassagesForInternalEvaluation() {
        List<LawPassage> ranked = List.of(new LawPassage(11L, 1L, "住房租赁条例", "第二十条",
                "出租人应当按照约定退还押金。", "https://www.gov.cn/law", 0.42D));
        LegalKnowledgeRetriever retriever = new StubRetriever(ranked);
        LegalSearchReqDTO request = new LegalSearchReqDTO();
        request.setQuery("押金退还");
        request.setRegionCode("CN");
        request.setAsOfDate("2026-10-04");
        request.setLimit(3);

        var result = new LegalKnowledgeSearchService(retriever).search(request);

        assertThat(result.getElapsedMillis()).isNotNegative();
        assertThat(result.getPassages()).singleElement().satisfies(passage -> {
            assertThat(passage.getChunkId()).isEqualTo(11L);
            assertThat(passage.getArticleNumber()).isEqualTo("第二十条");
            assertThat(passage.getScore()).isEqualTo(0.42D);
        });
    }

    private static final class StubRetriever extends LegalKnowledgeRetriever {
        private final List<LawPassage> ranked;

        private StubRetriever(List<LawPassage> ranked) {
            super(null, null, null, null, null, null);
            this.ranked = ranked;
        }

        @Override
        public List<LawPassage> retrieve(String query, String regionCode, LocalDate asOfDate, int limit) {
            assertThat(query).isEqualTo("押金退还");
            assertThat(regionCode).isEqualTo("CN");
            assertThat(asOfDate).isEqualTo(LocalDate.of(2026, 10, 4));
            assertThat(limit).isEqualTo(3);
            return ranked;
        }
    }
}
