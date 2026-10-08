package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.domain.entity.LegalSource;
import com.anyujia.anyujialegalservice.domain.entity.LegalSourceChunk;
import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import com.anyujia.anyujialegalservice.mapper.LegalSourceChunkMapper;
import com.anyujia.anyujialegalservice.mapper.LegalSourceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LegalKnowledgeRetriever {
    private static final int MAX_CANDIDATES = 2000;
    private final LegalSourceMapper sourceMapper;
    private final LegalSourceChunkMapper chunkMapper;
    private final LegalEmbeddingClient embeddingClient;
    private final LegalAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HybridLegalRanker ranker;

    public List<LawPassage> retrieve(String query, String regionCode, LocalDate asOfDate, int limit) {
        Map<String, String> singleQuery = new LinkedHashMap<>();
        singleQuery.put("single", query);
        return retrieveBatch(singleQuery, regionCode, asOfDate, limit)
                .getOrDefault("single", List.of());
    }

    public Map<String, List<LawPassage>> retrieveBatch(Map<String, String> queries, String regionCode,
                                                       LocalDate asOfDate, int limit) {
        if (queries == null || queries.isEmpty()) {
            return Map.of();
        }
        Map<String, List<LawPassage>> result = new LinkedHashMap<>();
        queries.keySet().forEach(key -> result.put(key, List.of()));
        String region = regionCode == null || regionCode.isBlank() ? "GLOBAL" : regionCode.toUpperCase(Locale.ROOT);
        LocalDate date = asOfDate == null ? LocalDate.now() : asOfDate;
        List<LegalSource> sources = sourceMapper.selectList(new LambdaQueryWrapper<LegalSource>()
                .eq(LegalSource::getStatus, 1));
        Map<Long, LegalSource> eligible = sources.stream()
                .filter(source -> isEligible(source, region, date))
                .collect(Collectors.toMap(LegalSource::getId, Function.identity()));
        if (eligible.isEmpty()) {
            return result;
        }
        List<String> queryTexts = new ArrayList<>(queries.values());
        List<Optional<double[]>> queryVectors = properties.getVector().isEnabled()
                ? embeddingClient.embedBatch(queryTexts)
                : queryTexts.stream().map(ignored -> Optional.<double[]>empty()).toList();
        boolean useVector = queryVectors.stream().anyMatch(Optional::isPresent);
        LambdaQueryWrapper<LegalSourceChunk> chunkQuery = new LambdaQueryWrapper<LegalSourceChunk>()
                .in(LegalSourceChunk::getSourceId, eligible.keySet());
        if (!useVector) {
            chunkQuery.select(LegalSourceChunk::getId, LegalSourceChunk::getSourceId,
                    LegalSourceChunk::getChunkIndex, LegalSourceChunk::getArticleNumber,
                    LegalSourceChunk::getChunkText);
        }
        List<LegalSourceChunk> chunks = chunkMapper.selectList(chunkQuery.last("LIMIT " + MAX_CANDIDATES));
        Map<Long, double[]> chunkVectors = useVector
                ? chunks.stream().collect(Collectors.toMap(LegalSourceChunk::getId,
                        chunk -> parseVector(chunk.getEmbeddingJson())))
                : Map.of();
        int queryIndex = 0;
        for (Map.Entry<String, String> queryEntry : queries.entrySet()) {
            Optional<double[]> queryVector = queryIndex < queryVectors.size()
                    ? queryVectors.get(queryIndex) : Optional.empty();
            result.put(queryEntry.getKey(), ranker.rank(queryEntry.getValue(), eligible,
                    chunks, queryVector, chunkVectors, limit));
            queryIndex++;
        }
        return result;
    }

    private boolean isEligible(LegalSource source, String region, LocalDate date) {
        boolean regionMatches = "GLOBAL".equalsIgnoreCase(source.getRegionCode())
                || region.equalsIgnoreCase(source.getRegionCode());
        return regionMatches && !source.getEffectiveFrom().isAfter(date)
                && (source.getEffectiveTo() == null || !source.getEffectiveTo().isBefore(date));
    }

    private double[] parseVector(String json) {
        if (json == null || json.isBlank()) {
            return new double[0];
        }
        try {
            return objectMapper.readValue(json, double[].class);
        } catch (Exception ex) {
            return new double[0];
        }
    }

}
