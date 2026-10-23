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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LegalKnowledgeRetriever {
    private final LegalSourceMapper sourceMapper;
    private final LegalSourceChunkMapper chunkMapper;
    private final LegalEmbeddingClient embeddingClient;
    private final LegalAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HybridLegalRanker ranker;
    private final ObjectProvider<LegalVectorStore> vectorStoreProvider;

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
                // When multiple effective versions overlap, prefer the newest version
                // for the same title and region; keep different statutes independent.
                .collect(Collectors.toMap(LegalSource::getId, Function.identity(),
                        (left, right) -> newerVersion(left, right), LinkedHashMap::new));
        Map<String, LegalSource> latestByTitle = new LinkedHashMap<>();
        eligible.values().forEach(source -> latestByTitle.merge(versionKey(source), source,
                this::newerVersion));
        eligible = latestByTitle.values().stream().collect(Collectors.toMap(
                LegalSource::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        if (eligible.isEmpty()) {
            return result;
        }
        List<String> queryTexts = new ArrayList<>(queries.values());
        List<Optional<double[]>> queryVectors = properties.getVector().isEnabled()
                ? embeddingClient.embedBatch(queryTexts)
                : queryTexts.stream().map(ignored -> Optional.<double[]>empty()).toList();
        boolean useVector = queryVectors.stream().anyMatch(Optional::isPresent);
        LegalVectorStore vectorStore = useVector ? vectorStoreProvider.getIfAvailable() : null;
        LambdaQueryWrapper<LegalSourceChunk> chunkQuery = new LambdaQueryWrapper<LegalSourceChunk>()
                .in(LegalSourceChunk::getSourceId, eligible.keySet());
        if (!useVector) {
            chunkQuery.select(LegalSourceChunk::getId, LegalSourceChunk::getSourceId,
                    LegalSourceChunk::getChunkIndex, LegalSourceChunk::getArticleNumber,
                    LegalSourceChunk::getChunkText);
        }
        int maxCandidates = properties.getRetrieval().getMaxChunkCandidates();
        List<LegalSourceChunk> chunks = chunkMapper.selectList(chunkQuery.last("LIMIT " + maxCandidates));
        Map<Long, double[]> chunkVectors = useVector
                ? chunks.stream().collect(Collectors.toMap(LegalSourceChunk::getId,
                        chunk -> parseVector(chunk.getEmbeddingJson())))
                : Map.of();
        int queryIndex = 0;
        for (Map.Entry<String, String> queryEntry : queries.entrySet()) {
            Optional<double[]> queryVector = queryIndex < queryVectors.size()
                    ? queryVectors.get(queryIndex) : Optional.empty();
            Map<Long, Double> vectorScores = vectorScores(queryVector, chunks, vectorStore,
                    chunkVectors, eligible);
            result.put(queryEntry.getKey(), ranker.rank(queryEntry.getValue(), eligible,
                    chunks, vectorScores, limit));
            queryIndex++;
        }
        return result;
    }

    private Map<Long, Double> vectorScores(Optional<double[]> queryVector, List<LegalSourceChunk> chunks,
                                           LegalVectorStore vectorStore, Map<Long, double[]> chunkVectors,
                                           Map<Long, LegalSource> eligible) {
        if (queryVector.isEmpty()) {
            return Map.of();
        }
        double[] vector = queryVector.get();
        if (vectorStore != null && vectorStore.isAvailable()) {
            return vectorStore.search(vector, properties.getMilvus().getTopK(), eligible.keySet()).stream()
                    .collect(Collectors.toMap(LegalVectorStore.VectorHit::chunkId,
                            LegalVectorStore.VectorHit::score, (left, right) -> left, LinkedHashMap::new));
        }
        Map<Long, Double> scores = new LinkedHashMap<>();
        for (LegalSourceChunk chunk : chunks) {
            double[] chunkVector = chunkVectors.getOrDefault(chunk.getId(), new double[0]);
            if (chunkVector.length == 0 || chunkVector.length != vector.length) {
                continue;
            }
            double similarity = cosine(vector, chunkVector);
            if (similarity > 0D) {
                scores.put(chunk.getId(), similarity);
            }
        }
        return scores;
    }

    private double cosine(double[] left, double[] right) {
        if (left.length == 0 || left.length != right.length) {
            return 0D;
        }
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        return leftNorm == 0D || rightNorm == 0D ? 0D : dot / Math.sqrt(leftNorm * rightNorm);
    }

    private boolean isEligible(LegalSource source, String region, LocalDate date) {
        boolean regionMatches = "GLOBAL".equalsIgnoreCase(source.getRegionCode())
                || region.equalsIgnoreCase(source.getRegionCode());
        return regionMatches && !source.getEffectiveFrom().isAfter(date)
                && (source.getEffectiveTo() == null || !source.getEffectiveTo().isBefore(date));
    }

    private String versionKey(LegalSource source) {
        return source.getRegionCode() + "|" + source.getTitle();
    }

    private LegalSource newerVersion(LegalSource left, LegalSource right) {
        if (left.getEffectiveFrom() == null) {
            return right;
        }
        if (right.getEffectiveFrom() == null) {
            return left;
        }
        return right.getEffectiveFrom().isAfter(left.getEffectiveFrom()) ? right : left;
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
