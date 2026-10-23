package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.domain.entity.LegalAgentMemorySnapshot;
import com.anyujia.anyujialegalservice.domain.model.AgentHistory;
import com.anyujia.anyujialegalservice.mapper.LegalAgentMemorySnapshotMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalAgentMemorySnapshotService {
    private static final long SNAPSHOT_TTL_DAYS = 30L;
    private final LegalAgentMemorySnapshotMapper mapper;
    private final ObjectMapper objectMapper;

    public LegalAgentMemorySnapshot load(Long tenantId, Long chatConversationId) {
        LegalAgentMemorySnapshot snapshot = mapper.selectOne(new LambdaQueryWrapper<LegalAgentMemorySnapshot>()
                .eq(LegalAgentMemorySnapshot::getTenantId, tenantId)
                .eq(LegalAgentMemorySnapshot::getChatConversationId, chatConversationId));
        if (snapshot != null && snapshot.getExpiresAt() != null
                && snapshot.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return snapshot;
    }

    public void upsert(Long tenantId, Long chatConversationId, List<AgentHistory.Turn> turns,
                       String contextHash) {
        if (turns == null || turns.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LegalAgentMemorySnapshot snapshot = load(tenantId, chatConversationId);
        if (snapshot == null) {
            snapshot = new LegalAgentMemorySnapshot();
            snapshot.setTenantId(tenantId);
            snapshot.setChatConversationId(chatConversationId);
            snapshot.setSnapshotVersion(1L);
            snapshot.setCreatedAt(now);
            snapshot.setExpiresAt(now.plusDays(SNAPSHOT_TTL_DAYS));
            try {
                mapper.insert(snapshot);
            } catch (DuplicateKeyException ignored) {
                snapshot = load(tenantId, chatConversationId);
            }
        }
        if (snapshot == null) {
            return;
        }
        snapshot.setSnapshotVersion((snapshot.getSnapshotVersion() == null ? 0L : snapshot.getSnapshotVersion()) + 1L);
        snapshot.setSummary(buildSummary(turns));
        snapshot.setFactsJson(toJson(turns));
        snapshot.setContextHash(contextHash);
        snapshot.setLastMessageAt(now);
        snapshot.setExpiresAt(now.plusDays(SNAPSHOT_TTL_DAYS));
        snapshot.setUpdatedAt(now);
        mapper.updateById(snapshot);
    }

    public List<AgentHistory.Turn> restoreAsHistory(Long tenantId, Long chatConversationId) {
        LegalAgentMemorySnapshot snapshot = load(tenantId, chatConversationId);
        if (snapshot == null || snapshot.getSummary() == null || snapshot.getSummary().isBlank()) {
            return List.of();
        }
        AgentHistory.Turn turn = new AgentHistory.Turn();
        turn.setRole("memory_snapshot");
        turn.setContent(snapshot.getSummary());
        return List.of(turn);
    }

    private String buildSummary(List<AgentHistory.Turn> turns) {
        return turns.stream().filter(turn -> turn != null && turn.getContent() != null)
                .skip(Math.max(0, turns.size() - 20L))
                .map(turn -> turn.getRole() + ": " + bounded(turn.getContent(), 800))
                .collect(Collectors.joining("\n"));
    }

    private String toJson(List<AgentHistory.Turn> turns) {
        try {
            return objectMapper.writeValueAsString(turns);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private String bounded(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
