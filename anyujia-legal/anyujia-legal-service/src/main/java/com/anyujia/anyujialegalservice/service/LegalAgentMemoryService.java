package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.domain.model.AgentHistory;
import com.anyujia.anyujiacommonredis.service.RedisService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalAgentMemoryService {
    private static final String AGENT_HISTORY_KEY_PREFIX = "legal:agent:history:";
    private static final long HISTORY_TTL_SECONDS = 1800L;

    private final LegalAiProperties properties;
    private final ObjectProvider<RedisService> redisServiceProvider;
    private final LegalAgentMemorySnapshotService snapshotService;
    private volatile boolean redisUnavailable;

    public List<AgentHistory.Turn> getHistory(Long tenantId, Long chatConversationId) {
        if (redisUnavailable) {
            return snapshotService.restoreAsHistory(tenantId, chatConversationId);
        }
        RedisService redisService = redisService();
        if (redisService == null) {
            return snapshotService.restoreAsHistory(tenantId, chatConversationId);
        }
        try {
            AgentHistory history = redisService.getCacheObject(
                    historyKey(tenantId, chatConversationId), AgentHistory.class);
            return history == null || history.getTurns() == null
                    ? snapshotService.restoreAsHistory(tenantId, chatConversationId) : history.getTurns();
        } catch (Exception ex) {
            redisUnavailable = true;
            log.warn("Legal agent history read disabled after Redis failure: {}", ex.getMessage());
            return snapshotService.restoreAsHistory(tenantId, chatConversationId);
        }
    }

    public void appendTurn(Long tenantId, Long chatConversationId, String role, String content) {
        if (redisUnavailable) {
            return;
        }
        RedisService redisService = redisService();
        if (redisService == null) {
            return;
        }
        try {
            String key = historyKey(tenantId, chatConversationId);
            AgentHistory history = redisService.getCacheObject(key, AgentHistory.class);
            if (history == null) {
                history = new AgentHistory();
                history.setTenantId(tenantId);
                history.setChatConversationId(chatConversationId);
            }
            AgentHistory.Turn turn = new AgentHistory.Turn();
            turn.setRole(role);
            turn.setContent(content);
            history.getTurns().add(turn);
            int limit = properties.getAgent().getHistoryLimit();
            if (history.getTurns().size() > limit) {
                history.setTurns(history.getTurns().subList(
                        history.getTurns().size() - limit, history.getTurns().size()));
            }
            history.setUpdatedAt(LocalDateTime.now());
            redisService.setCacheObject(key, history, HISTORY_TTL_SECONDS, TimeUnit.SECONDS);
            snapshotService.upsert(tenantId, chatConversationId, history.getTurns(), null);
        } catch (Exception ex) {
            redisUnavailable = true;
            log.warn("Legal agent history write disabled after Redis failure: {}", ex.getMessage());
            snapshotService.upsert(tenantId, chatConversationId,
                    getHistory(tenantId, chatConversationId), null);
        }
    }

    private RedisService redisService() {
        try {
            return redisServiceProvider.getIfAvailable();
        } catch (Exception ex) {
            redisUnavailable = true;
            return null;
        }
    }

    private String historyKey(Long tenantId, Long chatConversationId) {
        return AGENT_HISTORY_KEY_PREFIX + tenantId + ":" + chatConversationId;
    }
}
