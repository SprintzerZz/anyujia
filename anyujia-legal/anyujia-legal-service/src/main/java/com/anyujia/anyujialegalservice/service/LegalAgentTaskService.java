package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalapi.domain.dto.LegalAgentAnswerReqDTO;
import com.anyujia.anyujialegalapi.domain.dto.LegalAgentTaskSubmitReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.LegalAgentAnswerVO;
import com.anyujia.anyujialegalapi.domain.vo.LegalAgentTaskVO;
import com.anyujia.anyujialegalservice.domain.entity.LegalAgentTask;
import com.anyujia.anyujialegalservice.domain.entity.LegalAgentTaskOutbox;
import com.anyujia.anyujialegalservice.mapper.LegalAgentTaskMapper;
import com.anyujia.anyujialegalservice.mapper.LegalAgentTaskOutboxMapper;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalAgentTaskService {
    private static final String PENDING = "PENDING";
    private static final String RUNNING = "RUNNING";
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";
    private static final long LEASE_SECONDS = 300L;

    private final LegalAgentTaskMapper taskMapper;
    private final LegalAgentService agentService;
    private final ObjectMapper objectMapper;
    private final LegalAgentTaskOutboxMapper outboxMapper;
    private final LegalAgentTaskEventService eventService;

    @Transactional(rollbackFor = Exception.class)
    public LegalAgentTaskVO submit(Long tenantId, LegalAgentTaskSubmitReqDTO request) {
        LegalAgentTask existing = findByRequestId(tenantId, request.getRequestId());
        if (existing != null) {
            return toVO(existing);
        }
        LegalAgentTask task = new LegalAgentTask();
        task.setRequestId(request.getRequestId());
        task.setTenantId(tenantId);
        task.setChatConversationId(request.getChatConversationId());
        task.setQueryText(request.getQuery());
        task.setRegionCode(request.getRegionCode() == null || request.getRegionCode().isBlank()
                ? "GLOBAL" : request.getRegionCode().toUpperCase());
        task.setAsOfDate(request.getAsOfDate() == null || request.getAsOfDate().isBlank()
                ? LocalDate.now() : LocalDate.parse(request.getAsOfDate()));
        task.setStatus(PENDING);
        task.setAttemptCount(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            return toVO(findByRequestId(tenantId, request.getRequestId()));
        }
        LegalAgentTaskOutbox outbox = new LegalAgentTaskOutbox();
        outbox.setTaskId(task.getId());
        outbox.setEventType("AGENT_ANSWER");
        outbox.setPayload(String.valueOf(task.getId()));
        outbox.setStatus(0);
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(LocalDateTime.now());
        outbox.setCreatedAt(LocalDateTime.now());
        outbox.setUpdatedAt(LocalDateTime.now());
        outboxMapper.insert(outbox);
        return toVO(task);
    }

    public LegalAgentTaskVO get(Long tenantId, Long taskId) {
        LegalAgentTask task = taskMapper.selectOne(new LambdaQueryWrapper<LegalAgentTask>()
                .eq(LegalAgentTask::getId, taskId)
                .eq(LegalAgentTask::getTenantId, tenantId));
        if (task == null) {
            throw new ServiceException("法律助手任务不存在或无权访问", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
        return toVO(task);
    }

    public void execute(Long taskId) {
        String workerId = UUID.randomUUID().toString();
        LocalDateTime leaseUntil = LocalDateTime.now().plusSeconds(LEASE_SECONDS);
        if (taskMapper.claim(taskId, workerId, leaseUntil) == 0) {
            return;
        }
        LegalAgentTask task = taskMapper.selectById(taskId);
        try {
            LegalAgentAnswerReqDTO request = new LegalAgentAnswerReqDTO();
            request.setChatConversationId(task.getChatConversationId());
            request.setQuery(task.getQueryText());
            request.setRegionCode(task.getRegionCode());
            request.setAsOfDate(task.getAsOfDate().toString());
            LegalAgentAnswerVO answer = agentService.answer(task.getTenantId(), request);
            String answerJson = objectMapper.writeValueAsString(answer);
            if (taskMapper.complete(taskId, workerId, answerJson) > 0) {
                try {
                    eventService.append(taskId, task.getTenantId(), "result", answerJson);
                } catch (Exception eventError) {
                    // 任务状态已经完成，事件发布失败不能反向把任务改成 FAILED；后续可由事件补偿任务处理。
                    log.error("Legal Agent result event append failed, taskId={}", taskId, eventError);
                }
            }
        } catch (Exception ex) {
            log.error("Legal Agent task failed, taskId={}", taskId, ex);
            String error = boundedError(ex);
            if (taskMapper.fail(taskId, workerId, error) > 0) {
                try {
                    eventService.append(taskId, task.getTenantId(), "failed",
                            objectMapper.writeValueAsString(java.util.Map.of("errorMessage", error)));
                } catch (Exception eventError) {
                    log.error("Legal Agent failure event append failed, taskId={}", taskId, eventError);
                }
            }
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${legal.agent.recovery-delay-millis:30000}")
    public void reclaimExpiredTasks() {
        int reclaimed = taskMapper.reclaim(LocalDateTime.now());
        if (reclaimed > 0) {
            int revived = outboxMapper.reviveForPendingTasks();
            log.warn("Reclaimed expired Legal Agent tasks, count={}", reclaimed);
            log.info("Revived Legal Agent outbox events after task reclaim, count={}", revived);
        }
    }

    private LegalAgentTask findByRequestId(Long tenantId, String requestId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<LegalAgentTask>()
                .eq(LegalAgentTask::getTenantId, tenantId)
                .eq(LegalAgentTask::getRequestId, requestId));
    }

    private LegalAgentTaskVO toVO(LegalAgentTask task) {
        LegalAgentTaskVO result = new LegalAgentTaskVO();
        result.setTaskId(task.getId());
        result.setRequestId(task.getRequestId());
        result.setStatus(task.getStatus());
        result.setErrorMessage(task.getErrorMessage());
        result.setCreatedAt(toEpochMilli(task.getCreatedAt()));
        result.setCompletedAt(toEpochMilli(task.getCompletedAt()));
        if (COMPLETED.equals(task.getStatus()) && task.getAnswerJson() != null) {
            try {
                LegalAgentAnswerVO answer = objectMapper.readValue(task.getAnswerJson(), LegalAgentAnswerVO.class);
                result.setAnswerText(answer.getAnswerText());
                result.setCitations(answer.getCitations());
                result.setMode(answer.getMode());
                result.setDegraded(answer.isDegraded());
            } catch (JsonProcessingException ex) {
                result.setStatus(FAILED);
                result.setErrorMessage("任务结果解析失败");
            }
        }
        return result;
    }

    private String boundedError(Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private Long toEpochMilli(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
