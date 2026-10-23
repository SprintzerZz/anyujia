package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiachatapi.domain.dto.AgentMessageReqDTO;
import com.anyujia.anyujiachatapi.feign.ChatFeignClient;
import com.anyujia.anyujialegalapi.domain.vo.LegalAgentAnswerVO;
import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.domain.entity.LegalAgentTask;
import com.anyujia.anyujialegalservice.mapper.LegalAgentTaskMapper;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** DB-backed, at-least-once Chat delivery; Chat requestId makes retries idempotent. */
@Slf4j
@Service
public class LegalAgentAnswerDeliveryService {
    private final LegalAgentTaskMapper taskMapper;
    private final ChatFeignClient chatClient;
    private final ObjectMapper objectMapper;
    private final LegalAgentMemoryService memoryService;
    private final LegalAiProperties properties;
    private final Executor deliveryExecutor;

    public LegalAgentAnswerDeliveryService(LegalAgentTaskMapper taskMapper, ChatFeignClient chatClient,
                                           ObjectMapper objectMapper, LegalAgentMemoryService memoryService,
                                           LegalAiProperties properties,
                                           @Qualifier("legalAgentChatDeliveryExecutor") Executor deliveryExecutor) {
        this.taskMapper = taskMapper;
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.memoryService = memoryService;
        this.properties = properties;
        this.deliveryExecutor = deliveryExecutor;
    }

    @Scheduled(fixedDelayString = "${legal.agent.chat-delivery.poll-millis:1000}")
    public void deliverPending() {
        taskMapper.reclaimChatDelivery(LocalDateTime.now().minusMinutes(2));
        List<LegalAgentTask> pending = taskMapper.selectList(new LambdaQueryWrapper<LegalAgentTask>()
                .eq(LegalAgentTask::getStatus, "COMPLETED")
                .eq(LegalAgentTask::getContextConflict, false)
                .eq(LegalAgentTask::getChatDeliveryState, 0)
                .orderByAsc(LegalAgentTask::getId)
                .last("LIMIT 100"));
        pending.forEach(task -> schedule(task.getId()));
    }

    public void schedule(Long taskId) {
        try {
            deliveryExecutor.execute(() -> deliver(taskId));
        } catch (RejectedExecutionException ex) {
            // Task remains pending in MySQL and the next scheduled scan will retry.
            log.warn("Legal Agent Chat delivery executor full, taskId={}", taskId);
        }
    }

    public void deliver(Long taskId) {
        if (taskMapper.claimChatDelivery(taskId) == 0) {
            return;
        }
        try {
            LegalAgentTask task = taskMapper.selectById(taskId);
            LegalAgentAnswerVO answer = objectMapper.readValue(task.getAnswerJson(), LegalAgentAnswerVO.class);
            AgentMessageReqDTO message = new AgentMessageReqDTO();
            message.setChatConversationId(task.getChatConversationId());
            message.setRequestId("legal-agent-" + taskId);
            String content = answer.getAnswerText();
            message.setContent(content.substring(0,
                    Math.min(content.length(), properties.getAgent().getAnswerMaxLength())));
            var response = chatClient.sendAgentMessage(task.getTenantId(), "service", message);
            if (response == null || response.getCode() != ResultCode.SUCCESS.getCode()) {
                throw new IllegalStateException("Chat rejected Legal Agent answer");
            }
            if (taskMapper.markChatDelivered(taskId) > 0) {
                memoryService.appendTurn(task.getTenantId(), task.getChatConversationId(), "user", task.getQueryText());
                memoryService.appendTurn(task.getTenantId(), task.getChatConversationId(), "assistant", content);
            }
        } catch (Exception ex) {
            taskMapper.retryChatDelivery(taskId);
            log.warn("Legal Agent Chat delivery failed, taskId={}", taskId, ex);
        }
    }
}
