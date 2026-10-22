package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.domain.model.AgentHistory;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

/** Deterministically bounds Agent history while preserving turn source indexes. */
@Service
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class LegalAgentHistoryCompressor {
    private static final int DEFAULT_MAX_CHARS = 3_000;
    private static final int RECENT_TURNS = 6;
    private static final int OLD_TURN_PREVIEW_CHARS = 180;

    public String build(List<AgentHistory.Turn> turns) {
        return build(turns, DEFAULT_MAX_CHARS);
    }

    public String build(List<AgentHistory.Turn> turns, int maxChars) {
        if (turns == null || turns.isEmpty() || maxChars <= 0) {
            return "";
        }
        int recentStart = Math.max(0, turns.size() - RECENT_TURNS);
        StringBuilder result = new StringBuilder("[agent_history]\n");
        if (recentStart > 0) {
            result.append("[history_summary source_turns=1-").append(recentStart).append("]\n");
            for (int i = 0; i < recentStart && result.length() < maxChars; i++) {
                AgentHistory.Turn turn = turns.get(i);
                appendBounded(result, "turn=" + (i + 1) + " role=" + safe(turn.getRole())
                        + " preview=" + preview(turn.getContent()), maxChars);
            }
        }
        for (int i = recentStart; i < turns.size() && result.length() < maxChars; i++) {
            AgentHistory.Turn turn = turns.get(i);
            appendBounded(result, "[history_turn index=" + (i + 1) + " role="
                    + safe(turn.getRole()) + "]\n" + safe(turn.getContent()), maxChars);
        }
        return result.toString();
    }

    private String preview(String content) {
        String value = safe(content).replaceAll("\\s+", " ").trim();
        return value.length() <= OLD_TURN_PREVIEW_CHARS
                ? value : value.substring(0, OLD_TURN_PREVIEW_CHARS) + "...";
    }

    private void appendBounded(StringBuilder result, String value, int maxChars) {
        if (result.length() + value.length() + 1 <= maxChars) {
            result.append(value).append('\n');
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
