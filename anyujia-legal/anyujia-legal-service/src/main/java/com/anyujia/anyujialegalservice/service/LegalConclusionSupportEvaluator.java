package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalapi.domain.vo.LegalCitationVO;
import com.anyujia.anyujialegalapi.domain.vo.LegalConclusionSupportVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.integration.ai.NoopLegalEntailmentScorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Deterministic grounding gate; it never invents a citation and is auditable. */
@Service
public class LegalConclusionSupportEvaluator {
    private static final Pattern SEPARATOR = Pattern.compile("[。！？!?；;\\n]+|(?=\\d+[.)、])");
    private static final int MIN_CONCLUSION_LENGTH = 8;
    private final LegalEntailmentScorer entailmentScorer;
    private final LegalAiProperties properties;

    @Autowired
    public LegalConclusionSupportEvaluator(LegalEntailmentScorer entailmentScorer, LegalAiProperties properties) {
        this.entailmentScorer = entailmentScorer;
        this.properties = properties;
    }

    public LegalConclusionSupportEvaluator() {
        this(new NoopLegalEntailmentScorer(), new LegalAiProperties());
    }

    public List<LegalConclusionSupportVO> evaluate(String answer, List<LegalCitationVO> citations) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        List<String> conclusions = SEPARATOR.splitAsStream(answer)
                .map(String::trim)
                .filter(value -> value.length() >= MIN_CONCLUSION_LENGTH)
                .filter(value -> !value.startsWith("基于") && !value.startsWith("说明"))
                .toList();
        List<LegalConclusionSupportVO> result = new ArrayList<>();
        for (String conclusion : conclusions) {
            List<Match> matches = new ArrayList<>();
            if (citations != null) {
                for (int i = 0; i < citations.size(); i++) {
                    String quote = citations.get(i).getQuote();
                    Match match = semanticMatch(conclusion, quote, i + 1);
                    double score = match.score();
                    if (score > 0) {
                        matches.add(match);
                    }
                }
            }
            matches.sort(Comparator.comparingDouble(Match::score).reversed());
            double score = matches.isEmpty() ? 0D : Math.min(1D, matches.get(0).score());
            LegalConclusionSupportVO support = new LegalConclusionSupportVO();
            support.setConclusion(conclusion);
            support.setSupportScore(BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP));
            double supportedThreshold = properties.getLocalEntailment().getSupportedThreshold();
            double partialThreshold = properties.getLocalEntailment().getPartialThreshold();
            support.setSupportLevel(score >= supportedThreshold ? "SUPPORTED"
                    : score >= partialThreshold ? "PARTIAL" : "UNSUPPORTED");
            support.setCitationIndexes(matches.stream().limit(3).map(Match::index).toList());
            support.setSupportMethod(matches.isEmpty() ? method() : matches.get(0).method());
            support.setReason(matches.isEmpty() ? "未找到可核验的法规证据引用"
                    : matches.get(0).reason());
            result.add(support);
        }
        return result;
    }

    private Match semanticMatch(String conclusion, String quote, int index) {
        if (entailmentScorer.isAvailable()) {
            var result = entailmentScorer.score(quote, conclusion);
            if (result.isPresent()) {
                LegalEntailmentScorer.Result scored = result.get();
                if ("ENTAILMENT".equalsIgnoreCase(scored.label())) {
                    return new Match(index, scored.score(), "SEMANTIC_ENTAILMENT", "语义模型判定法规证据支持该结论");
                }
                if ("CONTRADICTION".equalsIgnoreCase(scored.label())) {
                    return new Match(index, 0D, "SEMANTIC_ENTAILMENT", "语义模型判定结论与该法规证据冲突");
                }
                return new Match(index, scored.score() * 0.5D, "SEMANTIC_ENTAILMENT", "语义模型判定证据不足以完全支持该结论");
            }
        }
        double lexical = overlap(conclusion, quote);
        return new Match(index, lexical, "LEXICAL_BASELINE", "语义模型不可用，仅检测到法规文本词面重合");
    }

    private String method() {
        return entailmentScorer.isAvailable() ? "SEMANTIC_ENTAILMENT" : "LEXICAL_BASELINE";
    }

    public BigDecimal minimumScore(List<LegalConclusionSupportVO> supports) {
        return supports == null || supports.isEmpty() ? BigDecimal.ZERO
                : supports.stream().map(LegalConclusionSupportVO::getSupportScore)
                .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
    }

    private double overlap(String left, String right) {
        if (right == null || right.isBlank()) {
            return 0D;
        }
        Set<String> leftTokens = tokens(left);
        Set<String> rightTokens = tokens(right);
        if (leftTokens.isEmpty()) {
            return 0D;
        }
        long hit = leftTokens.stream().filter(rightTokens::contains).count();
        return (double) hit / leftTokens.size();
    }

    private Set<String> tokens(String value) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || (c >= 0x4E00 && c <= 0x9FFF)) {
                result.add(String.valueOf(c));
            }
        }
        return result;
    }

    private record Match(int index, double score, String method, String reason) { }
}
