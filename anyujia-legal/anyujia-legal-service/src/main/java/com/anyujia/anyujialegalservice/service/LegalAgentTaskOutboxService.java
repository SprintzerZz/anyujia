package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.domain.entity.LegalAgentTaskOutbox;
import com.anyujia.anyujialegalservice.mapper.LegalAgentTaskOutboxMapper;
import com.anyujia.anyujialegalservice.mq.LegalAgentRabbitConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalAgentTaskOutboxService {
    private final LegalAgentTaskOutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;
    @Value("${legal.agent.outbox.batch-size:100}")
    private int batchSize;
    @Value("${legal.agent.outbox.max-retries:10}")
    private int maxRetries;
    @Value("${legal.agent.outbox.processing-timeout-millis:60000}")
    private long processingTimeoutMillis;

    @Scheduled(fixedDelayString = "${legal.agent.outbox.poll-millis:1000}")
    public void publishPending() {
        reclaimProcessing();
        List<LegalAgentTaskOutbox> rows = outboxMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LegalAgentTaskOutbox>()
                .eq(LegalAgentTaskOutbox::getStatus, 0)
                .le(LegalAgentTaskOutbox::getNextRetryAt, LocalDateTime.now())
                .orderByAsc(LegalAgentTaskOutbox::getId)
                .last("LIMIT " + Math.max(1, Math.min(batchSize, 500))));
        for (LegalAgentTaskOutbox row : rows) {
            String token = UUID.randomUUID().toString();
            if (outboxMapper.claim(row.getId(), token) == 0) {
                continue;
            }
            try {
                rabbitTemplate.convertAndSend(LegalAgentRabbitConfig.EXCHANGE,
                        LegalAgentRabbitConfig.ROUTING_KEY, row.getPayload());
                outboxMapper.markSent(row.getId(), token);
            } catch (Exception ex) {
                if (row.getRetryCount() >= maxRetries) {
                    outboxMapper.markFailed(row.getId(), token);
                    log.error("Legal Agent outbox exhausted retries, outboxId={}", row.getId(), ex);
                } else {
                    long delay = Math.min(300_000L, 1000L << Math.min(row.getRetryCount(), 8));
                    outboxMapper.retry(row.getId(), token, LocalDateTime.now().plusNanos(delay * 1_000_000L));
                    log.warn("Legal Agent outbox publish failed, outboxId={}, retryCount={}",
                            row.getId(), row.getRetryCount() + 1, ex);
                }
            }
        }
    }

    private void reclaimProcessing() {
        outboxMapper.reclaim(LocalDateTime.now().minusNanos(processingTimeoutMillis * 1_000_000L));
    }
}
