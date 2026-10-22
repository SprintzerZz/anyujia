package com.anyujia.anyujialegalservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AsyncLegalAgentTaskDispatcher {
    private final LegalAgentTaskService taskService;

    @Async("legalAgentExecutor")
    public void submit(Long taskId) {
        taskService.execute(taskId);
    }
}
