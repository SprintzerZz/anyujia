package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.anyujia.anyujialegalapi.domain.dto.LegalAnswerReqDTO;
import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import com.anyujia.anyujialegalservice.domain.model.LegalAnswerGeneration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegalAnswerServiceTest {
    @Test
    void exposesOnlyCitationsFromTheRetrievedAllowList() {
        List<LawPassage> passages = List.of(
                new LawPassage(11L, 1L, "住房租赁条例", "第十条", "押金约定规则", "https://gov.cn/a", 0.8),
                new LawPassage(12L, 2L, "中华人民共和国民法典", "第七百一十三条", "维修规则", "https://gov.cn/b", 0.7));
        LegalAnswerGenerator generator = (query, ignored) -> new LegalAnswerGeneration(
                "回答", List.of(1, 99, 1), "GENERATIVE_RAG", true, false);
        LegalAnswerService service = new LegalAnswerService(new StubRetriever(passages), generator);
        LegalAnswerReqDTO request = new LegalAnswerReqDTO();
        request.setQuery("押金能否扣除？");
        request.setRegionCode("CN");
        request.setAsOfDate("2026-10-04");
        request.setLimit(5);

        var result = service.answer(request);

        assertThat(result.getCitations()).singleElement().satisfies(citation -> {
            assertThat(citation.getIndex()).isEqualTo(1);
            assertThat(citation.getChunkId()).isEqualTo(11L);
            assertThat(citation.getArticleNumber()).isEqualTo("第十条");
        });
        assertThat(result.isAiGenerated()).isTrue();
        assertThat(result.getDisclaimer()).contains("不替代律师");
    }

    @Test
    void refusesQuestionsOutsideTheHousingRentalScopeBeforeRetrieval() {
        LegalKnowledgeRetriever retriever = new StubRetriever(List.of()) {
            @Override
            public List<LawPassage> retrieve(String query, String regionCode, LocalDate asOfDate, int limit) {
                throw new AssertionError("out-of-scope questions must not reach retrieval");
            }
        };
        LegalAnswerService service = new LegalAnswerService(retriever,
                (query, passages) -> { throw new AssertionError("generator must not be called"); });
        LegalAnswerReqDTO request = new LegalAnswerReqDTO();
        request.setQuery("公司拖欠工资应该怎么办？");

        var result = service.answer(request);

        assertThat(result.getMode()).isEqualTo("OUT_OF_SCOPE");
        assertThat(result.getCitations()).isEmpty();
        assertThat(result.getAnswer()).contains("不属于").contains("住房租赁");
    }

    @Test
    void refusesNonRentalDomainEvenWhenQueryMentionsRentalRegulation() {
        LegalKnowledgeRetriever retriever = new StubRetriever(List.of()) {
            @Override
            public List<LawPassage> retrieve(String query, String regionCode, LocalDate asOfDate, int limit) {
                throw new AssertionError("out-of-scope questions must not reach retrieval");
            }
        };
        LegalAnswerService service = new LegalAnswerService(retriever,
                (query, passages) -> { throw new AssertionError("generator must not be called"); });
        LegalAnswerReqDTO request = new LegalAnswerReqDTO();
        request.setQuery("\u533b\u9662\u624b\u672f\u5931\u8d25\u9020\u6210\u635f\u5bb3\uff0c"
                + "\u6211\u80fd\u7528\u4f4f\u623f\u79df\u8d41\u6761\u4f8b\u7d22\u8d54\u5417\uff1f");

        var result = service.answer(request);

        assertThat(result.getMode()).isEqualTo("OUT_OF_SCOPE");
        assertThat(result.getCitations()).isEmpty();
    }

    private static class StubRetriever extends LegalKnowledgeRetriever {
        private final List<LawPassage> passages;

        private StubRetriever(List<LawPassage> passages) {
            super(null, null, null, null, null, null, null);
            this.passages = passages;
        }

        @Override
        public List<LawPassage> retrieve(String query, String regionCode, LocalDate asOfDate, int limit) {
            assertThat(query).isEqualTo("押金能否扣除？");
            assertThat(regionCode).isEqualTo("CN");
            assertThat(asOfDate).isEqualTo(LocalDate.of(2026, 10, 4));
            assertThat(limit).isEqualTo(5);
            return passages;
        }
    }
}
