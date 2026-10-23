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
import org.springframework.stereotype.Service;

/** Deterministic grounding gate; it never invents a citation and is auditable. */
@Service
public class LegalConclusionSupportEvaluator {
    private static final Pattern SEPARATOR = Pattern.compile("[。！？!?；;\\n]+|(?=\\d+[.)、])");
    private static final int MIN_CONCLUSION_LENGTH = 8;

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
                    double score = overlap(conclusion, quote);
                    if (score > 0) {
                        matches.add(new Match(i + 1, score));
                    }
                }
            }
            matches.sort(Comparator.comparingDouble(Match::score).reversed());
            double score = matches.isEmpty() ? 0D : Math.min(1D, matches.get(0).score());
            LegalConclusionSupportVO support = new LegalConclusionSupportVO();
            support.setConclusion(conclusion);
            support.setSupportScore(BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP));
            support.setSupportLevel(score >= 0.6D ? "SUPPORTED" : score >= 0.3D ? "PARTIAL" : "UNSUPPORTED");
            support.setCitationIndexes(matches.stream().limit(3).map(Match::index).toList());
            support.setReason(matches.isEmpty() ? "未找到可核验的法规证据引用" : "结论与引用法规文本存在词面重合，需结合条款语义人工复核");
            result.add(support);
        }
        return result;
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

    private record Match(int index, double score) { }
}
