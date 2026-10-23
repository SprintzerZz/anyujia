package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.domain.entity.LegalAgentStageCheckpoint;
import com.anyujia.anyujialegalservice.mapper.LegalAgentStageCheckpointMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalAgentCheckpointService {
    private final LegalAgentStageCheckpointMapper mapper;

    public void start(Long taskId, String stage, String inputFingerprint) {
        LegalAgentStageCheckpoint checkpoint = new LegalAgentStageCheckpoint();
        checkpoint.setTaskId(taskId);
        checkpoint.setStage(stage);
        checkpoint.setStatus("RUNNING");
        checkpoint.setAttemptCount(1);
        checkpoint.setInputFingerprint(inputFingerprint);
        checkpoint.setStartedAt(LocalDateTime.now());
        checkpoint.setCreatedAt(checkpoint.getStartedAt());
        checkpoint.setUpdatedAt(checkpoint.getStartedAt());
        try {
            mapper.insert(checkpoint);
        } catch (DuplicateKeyException duplicate) {
            LegalAgentStageCheckpoint existing = mapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LegalAgentStageCheckpoint>()
                            .eq(LegalAgentStageCheckpoint::getTaskId, taskId)
                            .eq(LegalAgentStageCheckpoint::getStage, stage));
            if (existing != null) {
                existing.setStatus("RUNNING");
                existing.setAttemptCount(existing.getAttemptCount() == null ? 1 : existing.getAttemptCount() + 1);
                existing.setInputFingerprint(inputFingerprint);
                existing.setStartedAt(LocalDateTime.now());
                existing.setCompletedAt(null);
                existing.setErrorMessage(null);
                mapper.updateById(existing);
            }
        } catch (Exception ex) {
            log.warn("Legal Agent checkpoint start skipped, taskId={}, stage={}: {}", taskId, stage, ex.getMessage());
        }
    }

    public void success(Long taskId, String stage, String outputJson) {
        update(taskId, stage, "COMPLETED", outputJson, null);
    }

    public void failure(Long taskId, String stage, Exception error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        update(taskId, stage, "FAILED", null, message.substring(0, Math.min(1000, message.length())));
    }

    private void update(Long taskId, String stage, String status, String outputJson, String errorMessage) {
        try {
            LegalAgentStageCheckpoint checkpoint = mapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LegalAgentStageCheckpoint>()
                            .eq(LegalAgentStageCheckpoint::getTaskId, taskId)
                            .eq(LegalAgentStageCheckpoint::getStage, stage));
            if (checkpoint == null) {
                return;
            }
            checkpoint.setStatus(status);
            checkpoint.setOutputJson(outputJson);
            checkpoint.setErrorMessage(errorMessage);
            checkpoint.setCompletedAt(LocalDateTime.now());
            checkpoint.setUpdatedAt(checkpoint.getCompletedAt());
            mapper.updateById(checkpoint);
        } catch (Exception ex) {
            log.warn("Legal Agent checkpoint update skipped, taskId={}, stage={}: {}", taskId, stage, ex.getMessage());
        }
    }
}
