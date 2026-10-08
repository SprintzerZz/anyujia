package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anyujia.anyujialegalapi.domain.vo.EmbeddingBackfillVO;
import com.anyujia.anyujialegalservice.domain.entity.LegalSourceChunk;
import com.anyujia.anyujialegalservice.mapper.LegalSourceChunkMapper;
import com.anyujia.anyujialegalservice.mapper.LegalSourceMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LegalKnowledgeServiceTest {
    @Test
    void backfillsOnlyChunksThatCanBeEmbeddedAndReportsRemainingCount() {
        LegalSourceMapper sourceMapper = mock(LegalSourceMapper.class);
        LegalSourceChunkMapper chunkMapper = mock(LegalSourceChunkMapper.class);
        LegalSourceChunker chunker = new LegalSourceChunker();
        LegalEmbeddingClient embeddingClient = text -> "first".equals(text)
                ? Optional.of(new double[]{0.1d, 0.2d}) : Optional.empty();
        LegalKnowledgeService service = new LegalKnowledgeService(
                sourceMapper, chunkMapper, chunker, embeddingClient, new ObjectMapper());

        LegalSourceChunk first = chunk(1L, "first");
        LegalSourceChunk second = chunk(2L, "second");
        when(chunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
        when(chunkMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        EmbeddingBackfillVO result = service.backfillEmbeddings(500);

        assertThat(result.getSelectedCount()).isEqualTo(2);
        assertThat(result.getEmbeddedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getRemainingCount()).isEqualTo(1L);
        assertThat(first.getEmbeddingJson()).isEqualTo("[0.1,0.2]");
        assertThat(second.getEmbeddingJson()).isNull();
        verify(chunkMapper).updateById(first);
    }

    private LegalSourceChunk chunk(Long id, String text) {
        LegalSourceChunk chunk = new LegalSourceChunk();
        chunk.setId(id);
        chunk.setChunkText(text);
        return chunk;
    }
}
