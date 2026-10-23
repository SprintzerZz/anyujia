package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.mapper.LegalTraceEventMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LegalTraceArchiveTask {
    private final LegalTraceEventMapper mapper;

    @Value("${legal.trace.archive-enabled:true}")
    private boolean archiveEnabled;
    @Value("${legal.trace.retention-days:90}")
    private int retentionDays;
    @Value("${legal.trace.archive-batch-size:500}")
    private int batchSize;

    @Scheduled(cron = "${legal.trace.archive-cron:0 20 3 * * *}")
    @Transactional(rollbackFor = Exception.class)
    public void archive() {
        if (!archiveEnabled || retentionDays < 1 || batchSize < 1) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int archived = mapper.archiveBefore(cutoff, batchSize);
        if (archived > 0) {
            int deleted = mapper.deleteArchivedBefore(cutoff, batchSize);
            log.info("Archived legal trace events: archived={}, deleted={}, cutoff={}",
                    archived, deleted, cutoff);
        }
    }
}
