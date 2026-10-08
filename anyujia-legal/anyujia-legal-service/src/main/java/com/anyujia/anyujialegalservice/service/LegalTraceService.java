package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.domain.entity.LegalTraceEvent;
import com.anyujia.anyujialegalservice.mapper.LegalTraceEventMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalTraceService {
    private static final String RUNNING = "RUNNING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";

    private final LegalTraceEventMapper mapper;
    private final ObjectMapper objectMapper;

    public LegalTraceEvent start(Long jobId, String stage, Map<String, ?> details) {
        LegalTraceEvent event = new LegalTraceEvent();
        event.setJobId(jobId);
        event.setStage(stage);
        event.setStatus(RUNNING);
        event.setStartedAt(LocalDateTime.now());
        event.setCreatedAt(event.getStartedAt());
        event.setDetailsJson(toJson(details));
        insertQuietly(event);
        return event;
    }

    public void success(LegalTraceEvent event, Map<String, ?> details) {
        complete(event, SUCCESS, details);
    }

    public void failure(LegalTraceEvent event, Exception ex, Map<String, ?> details) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (details != null) {
            merged.putAll(details);
        }
        merged.put("errorType", ex.getClass().getSimpleName());
        String message = ex.getMessage();
        if (message != null && !message.isBlank()) {
            merged.put("errorMessage", message.substring(0, Math.min(message.length(), 500)));
        }
        complete(event, FAILED, merged);
    }

    public void record(Long jobId, String stage, String status, Map<String, ?> details) {
        LegalTraceEvent event = start(jobId, stage, details);
        complete(event, status, details);
    }

    private void complete(LegalTraceEvent event, String status, Map<String, ?> details) {
        if (event == null || event.getId() == null) {
            return;
        }
        LocalDateTime endedAt = LocalDateTime.now();
        event.setStatus(status);
        event.setEndedAt(endedAt);
        event.setDurationMillis(Duration.between(event.getStartedAt(), endedAt).toMillis());
        event.setDetailsJson(toJson(details));
        try {
            mapper.updateById(event);
        } catch (Exception ex) {
            log.warn("Legal trace update skipped, jobId={}, stage={}: {}",
                    event.getJobId(), event.getStage(), ex.getMessage());
        }
    }

    private void insertQuietly(LegalTraceEvent event) {
        try {
            mapper.insert(event);
        } catch (Exception ex) {
            log.warn("Legal trace insert skipped, jobId={}, stage={}: {}",
                    event.getJobId(), event.getStage(), ex.getMessage());
        }
    }

    private String toJson(Map<String, ?> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":\"" + ex.getClass().getSimpleName() + "\"}";
        }
    }
}
