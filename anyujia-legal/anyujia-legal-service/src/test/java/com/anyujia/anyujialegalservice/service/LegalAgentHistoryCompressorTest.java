package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.domain.model.AgentHistory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegalAgentHistoryCompressorTest {
    @Test
    void summarizesOldTurnsAndKeepsRecentTurnsVerbatim() {
        List<AgentHistory.Turn> turns = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            AgentHistory.Turn turn = new AgentHistory.Turn();
            turn.setRole(i % 2 == 0 ? "assistant" : "user");
            turn.setContent("第" + i + "轮关于押金和维修的追问");
            turns.add(turn);
        }

        String context = new LegalAgentHistoryCompressor().build(turns);

        assertThat(context).contains("history_summary source_turns=1-2");
        assertThat(context).contains("history_turn index=3", "history_turn index=8");
        assertThat(context).contains("第8轮关于押金和维修的追问");
    }

    @Test
    void neverExceedsConfiguredBudget() {
        AgentHistory.Turn turn = new AgentHistory.Turn();
        turn.setRole("user");
        turn.setContent("x".repeat(5000));

        String context = new LegalAgentHistoryCompressor().build(List.of(turn), 100);

        assertThat(context.length()).isLessThanOrEqualTo(100);
    }

    @Test
    void recentTurnsTakePriorityWhenOlderPreviewsWouldFillBudget() {
        List<AgentHistory.Turn> turns = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            AgentHistory.Turn turn = new AgentHistory.Turn();
            turn.setRole("user");
            turn.setContent(i == 29 ? "最新风险问题" : "旧内容".repeat(40));
            turns.add(turn);
        }
        String context = new LegalAgentHistoryCompressor().build(turns, 1000);
        assertThat(context).contains("最新风险问题", "history_turn index=30");
    }
}
