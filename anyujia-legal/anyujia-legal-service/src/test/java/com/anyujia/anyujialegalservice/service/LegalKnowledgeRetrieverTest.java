package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.domain.entity.LegalSource;
import com.anyujia.anyujialegalservice.domain.entity.LegalSourceChunk;
import com.anyujia.anyujialegalservice.mapper.LegalSourceChunkMapper;
import com.anyujia.anyujialegalservice.mapper.LegalSourceMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LegalKnowledgeRetrieverTest {
    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), "test");
        TableInfoHelper.initTableInfo(assistant, LegalSource.class);
        TableInfoHelper.initTableInfo(assistant, LegalSourceChunk.class);
    }

    @Test
    void retrievesByLexicalFallbackWithinRegionAndEffectiveDate() {
        LegalSourceMapper sourceMapper = mock(LegalSourceMapper.class);
        LegalSourceChunkMapper chunkMapper = mock(LegalSourceChunkMapper.class);
        LegalSource source = new LegalSource();
        source.setId(1L);
        source.setTitle("住房租赁规定");
        source.setRegionCode("CN-SH");
        source.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        source.setSourceUrl("https://example.gov.cn/law");
        when(sourceMapper.selectList(any())).thenReturn(List.of(source));
        LegalSourceChunk chunk = new LegalSourceChunk();
        chunk.setId(2L);
        chunk.setSourceId(1L);
        chunk.setArticleNumber("第十条");
        chunk.setChunkText("出租人收取押金的，应当按照约定及时退还押金。");
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
        LegalEmbeddingClient embeddings = text -> Optional.empty();
        LegalAiProperties properties = new LegalAiProperties();
        LegalKnowledgeRetriever retriever = new LegalKnowledgeRetriever(
                sourceMapper, chunkMapper, embeddings, properties, new ObjectMapper(),
                new HybridLegalRanker(properties));

        var result = retriever.retrieve("押金退还", "CN-SH", LocalDate.of(2026, 1, 1), 3);

        assertThat(result).singleElement().satisfies(passage -> {
            assertThat(passage.articleNumber()).isEqualTo("第十条");
            assertThat(passage.sourceUrl()).isEqualTo("https://example.gov.cn/law");
        });
        ArgumentCaptor<Wrapper<LegalSourceChunk>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(chunkMapper).selectList(queryCaptor.capture());
        var capturedQuery = (LambdaQueryWrapper<LegalSourceChunk>) queryCaptor.getValue();
        assertThat(capturedQuery.getSqlSelect()).doesNotContain("embedding_json");
    }

    @Test
    void generatesQueryEmbeddingsInOneBatch() {
        LegalSourceMapper sourceMapper = mock(LegalSourceMapper.class);
        LegalSourceChunkMapper chunkMapper = mock(LegalSourceChunkMapper.class);
        LegalSource source = new LegalSource();
        source.setId(1L);
        source.setTitle("住房租赁条例");
        source.setRegionCode("CN");
        source.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        when(sourceMapper.selectList(any())).thenReturn(List.of(source));
        LegalSourceChunk chunk = new LegalSourceChunk();
        chunk.setId(2L);
        chunk.setSourceId(1L);
        chunk.setArticleNumber("第十条");
        chunk.setChunkText("出租人收取押金的，应当约定返还时间。");
        chunk.setEmbeddingJson("[1.0,0.0]");
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
        AtomicInteger batchCalls = new AtomicInteger();
        LegalEmbeddingClient embeddings = new LegalEmbeddingClient() {
            @Override
            public Optional<double[]> embed(String text) {
                throw new AssertionError("single embedding call should not be used");
            }

            @Override
            public List<Optional<double[]>> embedBatch(List<String> texts) {
                batchCalls.incrementAndGet();
                return texts.stream().map(ignored -> Optional.of(new double[]{1D, 0D})).toList();
            }
        };
        LegalAiProperties properties = new LegalAiProperties();
        properties.getVector().setEnabled(true);
        LegalKnowledgeRetriever retriever = new LegalKnowledgeRetriever(
                sourceMapper, chunkMapper, embeddings, properties, new ObjectMapper(),
                new HybridLegalRanker(properties));
        LinkedHashMap<String, String> queries = new LinkedHashMap<>();
        queries.put("deposit-contract", "押金返还时间");
        queries.put("deposit-chat", "无正当理由扣减押金");

        var result = retriever.retrieveBatch(queries, "CN", LocalDate.of(2026, 1, 1), 1);

        assertThat(batchCalls).hasValue(1);
        assertThat(result).containsOnlyKeys("deposit-contract", "deposit-chat");
        assertThat(result.values()).allSatisfy(passages -> assertThat(passages).hasSize(1));
    }
}
