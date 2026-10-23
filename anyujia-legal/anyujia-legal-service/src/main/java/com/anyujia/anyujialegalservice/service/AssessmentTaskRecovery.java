package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.constants.LegalConstants;
import com.anyujia.anyujialegalservice.domain.entity.LegalAssessmentJob;
import com.anyujia.anyujialegalservice.mapper.LegalAssessmentJobMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Re-dispatches jobs lost after commit but before the local async executor accepted them. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssessmentTaskRecovery {
    private static final int BATCH_SIZE = 100;

    private final LegalAssessmentJobMapper jobMapper;
    private final AssessmentTaskDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${legal.assessment.recovery-delay-millis:30000}",
            initialDelayString = "${legal.assessment.recovery-initial-delay-millis:10000}")
    public void recoverPendingJobs() {
        List<LegalAssessmentJob> jobs = jobMapper.selectList(new LambdaQueryWrapper<LegalAssessmentJob>()
                .and(wrapper -> wrapper
                        .and(pending -> pending.eq(LegalAssessmentJob::getStatus, LegalConstants.JOB_PENDING)
                                .le(LegalAssessmentJob::getNextRunAt, LocalDateTime.now()))
                        .or(running -> running.eq(LegalAssessmentJob::getStatus, LegalConstants.JOB_RUNNING)
                                .lt(LegalAssessmentJob::getLeaseUntil, LocalDateTime.now())))
                .orderByAsc(LegalAssessmentJob::getCreatedAt)
                .last("LIMIT " + BATCH_SIZE));
        for (LegalAssessmentJob job : jobs) {
            try {
                dispatcher.submit(job.getId());
            } catch (RuntimeException ex) {
                log.warn("Failed to re-dispatch legal assessment job {}: {}", job.getId(), ex.getMessage());
            }
        }
    }
}
