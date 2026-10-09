package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.domain.entity.LegalSource;
import com.anyujia.anyujialegalservice.domain.entity.LegalSourceChunk;
import com.anyujia.anyujialegalservice.integration.ai.NoopLegalReranker;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HybridLegalRankerTest {
    @Test
    void fusesLexicalVectorAndLegalIntentThenReranksAuthoritativeEvidence() {
        LegalAiProperties properties = new LegalAiProperties();
        HybridLegalRanker ranker = new HybridLegalRanker(properties, new NoopLegalReranker());
        LegalSource official = source(1L, "住房租赁条例", "国务院", "https://www.gov.cn/law");
        LegalSource blog = source(2L, "租房博客", null, "https://example.com/post");
        LegalSourceChunk officialDeposit = chunk(11L, 1L, "第二十条",
                "出租人收取押金的，应当约定押金返还时间和扣除情形。");
        LegalSourceChunk vectorOnly = chunk(12L, 2L, null, "居住期间应保持房间整洁。");
        LegalSourceChunk unrelated = chunk(13L, 2L, null, "租赁房屋维修责任由双方约定。");

        var result = ranker.rank("押金什么时候退还", Map.of(1L, official, 2L, blog),
                List.of(vectorOnly, unrelated, officialDeposit),
                Map.of(11L, 0.97D, 12L, 1.0D, 13L, 0.0D), 3);

        assertThat(result).extracting(passage -> passage.chunkId())
                .startsWith(11L)
                .contains(12L);
        assertThat(result.get(0).articleNumber()).isEqualTo("第二十条");
    }

    @Test
    void fallsBackToIntentAndLexicalChannelsWithoutQueryVector() {
        LegalAiProperties properties = new LegalAiProperties();
        HybridLegalRanker ranker = new HybridLegalRanker(properties, new NoopLegalReranker());
        LegalSource source = source(1L, "民法典", "全国人民代表大会", "https://www.gov.cn/law");
        LegalSourceChunk deposit = chunk(11L, 1L, "第七百零三条", "保证金扣除应当具有合同依据。");

        var result = ranker.rank("押金被没收", Map.of(1L, source), List.of(deposit), Map.of(), 1);

        assertThat(result).singleElement().satisfies(passage -> {
            assertThat(passage.chunkId()).isEqualTo(11L);
            assertThat(passage.score()).isPositive();
        });
    }

    @Test
    void bridgesColloquialRentalLanguageToStatutoryTerms() {
        LegalAiProperties properties = new LegalAiProperties();
        HybridLegalRanker ranker = new HybridLegalRanker(properties, new NoopLegalReranker());
        LegalSource source = source(1L, "住房租赁条例", "国务院", "https://www.gov.cn/law");
        LegalSourceChunk entryRule = chunk(11L, 1L, "第九条",
                "出租人不得擅自进入租赁住房，但是经承租人同意或者依法可以进入的除外。");
        LegalSourceChunk unrelated = chunk(12L, 1L, "第三十条", "住房租赁企业应当建立内部管理制度。");

        var result = ranker.rank("房东能随时自己开门进来检查房屋吗", Map.of(1L, source),
                List.of(unrelated, entryRule), Map.of(), 2);

        assertThat(result).extracting(passage -> passage.chunkId()).startsWith(11L);
    }

    private LegalSource source(Long id, String title, String issuer, String url) {
        LegalSource source = new LegalSource();
        source.setId(id);
        source.setTitle(title);
        source.setIssuer(issuer);
        source.setSourceUrl(url);
        source.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        return source;
    }

    private LegalSourceChunk chunk(Long id, Long sourceId, String article, String text) {
        LegalSourceChunk chunk = new LegalSourceChunk();
        chunk.setId(id);
        chunk.setSourceId(sourceId);
        chunk.setArticleNumber(article);
        chunk.setChunkText(text);
        return chunk;
    }
}
