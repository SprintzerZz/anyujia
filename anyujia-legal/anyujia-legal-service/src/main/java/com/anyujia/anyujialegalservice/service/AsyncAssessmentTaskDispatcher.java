package com.anyujia.anyujialegalservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AsyncAssessmentTaskDispatcher implements AssessmentTaskDispatcher {
    private final AssessmentWorker worker;

    @Override
    @Async("legalAssessmentExecutor")
    public void submit(Long jobId) {
        worker.execute(jobId);
    }
}
