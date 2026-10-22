package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.domain.entity.LegalAgentTaskEvent;
import com.anyujia.anyujialegalservice.mapper.LegalAgentTaskEventMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalAgentTaskEventService {
    private final LegalAgentTaskEventMapper eventMapper;
    private final StringRedisTemplate redisTemplate;
    private final LegalAgentMetrics metrics;
    @Value("${legal.agent.events.stream-key:legal:agent:events}")
    private String streamKey;

    public LegalAgentTaskEvent append(Long taskId, Long tenantId, String eventType, String payload) {
        LegalAgentTaskEvent event = new LegalAgentTaskEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setRedisPublished(0);
        event.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(event);
        publish(event);
        return event;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${legal.agent.events.compensation-delay-millis:5000}")
    public void compensateUnpublishedEvents() {
        List<LegalAgentTaskEvent> events = eventMapper.selectList(new LambdaQueryWrapper<LegalAgentTaskEvent>()
                .eq(LegalAgentTaskEvent::getRedisPublished, 0)
                .orderByAsc(LegalAgentTaskEvent::getId)
                .last("LIMIT 100"));
        events.forEach(this::publish);
        if (!events.isEmpty()) {
            metrics.recordCompensation(events.size());
        }
    }

    public List<LegalAgentTaskEvent> after(Long tenantId, Long taskId, Long lastEventId, int limit) {
        return eventMapper.selectList(new LambdaQueryWrapper<LegalAgentTaskEvent>()
                .eq(LegalAgentTaskEvent::getTenantId, tenantId)
                .eq(LegalAgentTaskEvent::getTaskId, taskId)
                .gt(lastEventId != null && lastEventId > 0, LegalAgentTaskEvent::getId,
                        lastEventId == null ? 0L : lastEventId)
                .orderByAsc(LegalAgentTaskEvent::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    }

    private void publish(LegalAgentTaskEvent event) {
        try {
            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .ofMap(java.util.Map.of(
                            "eventId", String.valueOf(event.getId()),
                            "taskId", String.valueOf(event.getTaskId()),
                            "tenantId", String.valueOf(event.getTenantId()),
                            "eventType", event.getEventType()))
                    .withStreamKey(streamKey);
            redisTemplate.opsForStream().add(record);
            event.setRedisPublished(1);
            eventMapper.updateById(event);
            metrics.recordPublished();
        } catch (Exception ex) {
            // MySQL is the replay source of truth; Redis is only the cross-instance wake-up path.
            metrics.recordPublishFailure();
            log.warn("Legal Agent event published to Redis Stream failed, eventId={}", event.getId(), ex);
        }
    }
}
