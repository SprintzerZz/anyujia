package com.anyujia.anyujialegalservice.integration.ai;

import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import com.anyujia.anyujialegalservice.domain.model.LegalAnswerGeneration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

final class ExtractiveLegalAnswer {
    private static final int MAX_PASSAGES = 5;
    private static final int MAX_QUOTE_LENGTH = 260;

    private ExtractiveLegalAnswer() {
    }

    static LegalAnswerGeneration compose(List<LawPassage> passages, String mode) {
        if (passages == null || passages.isEmpty()) {
            return new LegalAnswerGeneration(
                    "当前法规库中没有检索到足够依据，建议补充地区、日期和具体事实后再核对。",
                    List.of(), "NO_EVIDENCE", false, true);
        }
        List<Integer> indexes = new ArrayList<>();
        StringBuilder answer = new StringBuilder("根据当前检索到的法规资料：");
        int count = Math.min(MAX_PASSAGES, passages.size());
        for (int i = 0; i < count; i++) {
            LawPassage passage = passages.get(i);
            indexes.add(i + 1);
            answer.append("\n[").append(i + 1).append("] ");
            if (StringUtils.hasText(passage.articleNumber())) {
                answer.append(passage.articleNumber()).append("：");
            }
            answer.append(bounded(passage.text()));
        }
        answer.append("\n具体结论仍需结合合同约定、完整事实和当地规则判断。");
        return new LegalAnswerGeneration(answer.toString(), indexes, mode, false, true);
    }

    private static String bounded(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_QUOTE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_QUOTE_LENGTH) + "…";
    }
}
