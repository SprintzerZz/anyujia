package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalapi.domain.dto.LegalSourceIngestReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.EmbeddingBackfillVO;
import com.anyujia.anyujialegalapi.domain.vo.LegalSourceVO;
import com.anyujia.anyujialegalservice.domain.entity.LegalSource;
import com.anyujia.anyujialegalservice.domain.entity.LegalSourceChunk;
import com.anyujia.anyujialegalservice.mapper.LegalSourceChunkMapper;
import com.anyujia.anyujialegalservice.mapper.LegalSourceMapper;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LegalKnowledgeService {
    private final LegalSourceMapper sourceMapper;
    private final LegalSourceChunkMapper chunkMapper;
    private final LegalSourceChunker chunker;
    private final LegalEmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<LegalVectorStore> vectorStoreProvider;

    @Transactional(rollbackFor = Exception.class)
    public LegalSourceVO ingest(LegalSourceIngestReqDTO request) {
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(request.getEffectiveFrom());
            to = request.getEffectiveTo() == null || request.getEffectiveTo().isBlank()
                    ? null : LocalDate.parse(request.getEffectiveTo());
        } catch (DateTimeParseException ex) {
            throw new ServiceException("法规生效日期格式无效", ResultCode.INVALID_PARA.getCode());
        }
        if (to != null && to.isBefore(from)) {
            throw new ServiceException("法规失效日期不能早于生效日期", ResultCode.INVALID_PARA.getCode());
        }
        String hash = sha256(request.getFullText());
        LegalSource existing = sourceMapper.selectOne(new LambdaQueryWrapper<LegalSource>()
                .eq(LegalSource::getContentHash, hash).last("LIMIT 1"));
        if (existing != null) {
            List<LegalSourceChunk> existingChunks = chunkMapper.selectList(new LambdaQueryWrapper<LegalSourceChunk>()
                    .eq(LegalSourceChunk::getSourceId, existing.getId()));
            return toVO(existing, existingChunks.size(),
                    (int) existingChunks.stream().filter(chunk -> chunk.getEmbeddingJson() != null).count());
        }
        List<LegalSourceChunker.LawChunk> chunks = chunker.split(request.getFullText());
        if (chunks.isEmpty()) {
            throw new ServiceException("法规正文不能为空", ResultCode.INVALID_PARA.getCode());
        }
        LegalSource source = new LegalSource();
        source.setTitle(request.getTitle());
        source.setIssuer(request.getIssuer());
        source.setRegionCode(request.getRegionCode().toUpperCase());
        source.setEffectiveFrom(from);
        source.setEffectiveTo(to);
        source.setSourceUrl(request.getSourceUrl());
        source.setContentHash(hash);
        source.setStatus(1);
        source.setCreatedAt(LocalDateTime.now());
        source.setUpdatedAt(source.getCreatedAt());
        sourceMapper.insert(source);
        LegalVectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        for (int i = 0; i < chunks.size(); i++) {
            LegalSourceChunker.LawChunk chunk = chunks.get(i);
            Optional<double[]> vector = embeddingClient.embed(chunk.text());
            LegalSourceChunk entity = new LegalSourceChunk();
            entity.setSourceId(source.getId());
            entity.setChunkIndex(i);
            entity.setArticleNumber(chunk.articleLabel());
            entity.setChunkText(chunk.text());
            entity.setEmbeddingJson(vector.map(this::toJson).orElse(null));
            chunkMapper.insert(entity);
            vector.ifPresent(value -> upsertToVectorStore(vectorStore, entity, source, value));
        }
        int embedded = Math.toIntExact(chunkMapper.selectCount(new LambdaQueryWrapper<LegalSourceChunk>()
                .eq(LegalSourceChunk::getSourceId, source.getId())
                .isNotNull(LegalSourceChunk::getEmbeddingJson)));
        return toVO(source, chunks.size(), embedded);
    }

    public EmbeddingBackfillVO backfillEmbeddings(Integer requestedLimit) {
        int limit = requestedLimit == null ? 100 : Math.max(1, Math.min(requestedLimit, 100));
        List<LegalSourceChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<LegalSourceChunk>()
                .isNull(LegalSourceChunk::getEmbeddingJson)
                .orderByAsc(LegalSourceChunk::getId)
                .last("LIMIT " + limit));
        LegalVectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        Map<Long, LegalSource> sourcesById = chunks.isEmpty() ? Map.of()
                : sourceMapper.selectList(new LambdaQueryWrapper<LegalSource>()
                        .in(LegalSource::getId, chunks.stream()
                                .map(LegalSourceChunk::getSourceId).distinct().toList()))
                .stream().collect(Collectors.toMap(LegalSource::getId, Function.identity()));
        int embedded = 0;
        for (LegalSourceChunk chunk : chunks) {
            Optional<double[]> vector = embeddingClient.embed(chunk.getChunkText());
            if (vector.isEmpty()) {
                continue;
            }
            chunk.setEmbeddingJson(toJson(vector.get()));
            chunkMapper.updateById(chunk);
            upsertToVectorStore(vectorStore, chunk, sourcesById.get(chunk.getSourceId()), vector.get());
            embedded++;
        }
        EmbeddingBackfillVO result = new EmbeddingBackfillVO();
        result.setSelectedCount(chunks.size());
        result.setEmbeddedCount(embedded);
        result.setFailedCount(chunks.size() - embedded);
        result.setRemainingCount(chunkMapper.selectCount(new LambdaQueryWrapper<LegalSourceChunk>()
                .isNull(LegalSourceChunk::getEmbeddingJson)));
        return result;
    }

    private void upsertToVectorStore(LegalVectorStore vectorStore, LegalSourceChunk chunk,
                                     LegalSource source, double[] vector) {
        if (vectorStore == null || source == null || vector == null || vector.length == 0) {
            return;
        }
        vectorStore.upsert(chunk.getId(), chunk.getSourceId(), chunk.getArticleNumber(),
                source.getTitle(), source.getSourceUrl(), source.getRegionCode(), vector);
    }

    private LegalSourceVO toVO(LegalSource source, int chunkCount, int embeddedChunkCount) {
        LegalSourceVO result = new LegalSourceVO();
        result.setSourceId(source.getId());
        result.setTitle(source.getTitle());
        result.setChunkCount(chunkCount);
        result.setEmbeddedChunkCount(embeddedChunkCount);
        return result;
    }

    private String toJson(double[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
