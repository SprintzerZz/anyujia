package com.anyujia.anyujiachatservice.sharding;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verifies the legacy table and shard tables before traffic is switched. */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chat.message-sharding.verification", name = "enabled",
        havingValue = "true")
public class ChatMessageShardVerificationService {
    private final JdbcTemplate jdbcTemplate;
    private final ChatMessageShardRouter shardRouter;

    @Value("${chat.message-sharding.verification.sample-size:1000}")
    private int sampleSize;

    @Scheduled(fixedDelayString = "${chat.message-sharding.verification.fixed-delay-millis:60000}")
    @Transactional(readOnly = true)
    public void verify() {
        long sourceTotal = count("chat_message");
        long shardTotal = 0;
        long missingTotal = 0;
        List<ShardReport> reports = new ArrayList<>();
        for (int shard = 0; shard < shardRouter.physicalShardCount(); shard++) {
            String table = String.format("chat_message_s%02d", shard);
            long sourceCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM chat_message WHERE MOD(MOD(conversation_id, 1024), ?) = ?",
                    Long.class, shardRouter.physicalShardCount(), shard);
            long targetCount = count(table);
            long missing = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                      FROM chat_message source
                 LEFT JOIN %s target ON target.id = source.id
                     WHERE MOD(MOD(source.conversation_id, 1024), ?) = ?
                       AND target.id IS NULL
                    """.formatted(table), Long.class,
                    shardRouter.physicalShardCount(), shard);
            reports.add(new ShardReport(shard, sourceCount, targetCount, missing));
            shardTotal += targetCount;
            missingTotal += missing;
        }
        boolean complete = sourceTotal == shardTotal && missingTotal == 0;
        if (complete) {
            log.info("Chat message shard verification passed: sourceTotal={}, shardTotal={}, sampleSize={}",
                    sourceTotal, shardTotal, Math.max(1, sampleSize));
        } else {
            log.error("Chat message shard verification failed: sourceTotal={}, shardTotal={}, missing={}, "
                            + "details={}", sourceTotal, shardTotal, missingTotal, reports);
        }
    }

    private long count(String table) {
        if (!table.equals("chat_message") && !table.matches("chat_message_s\\d{2}")) {
            throw new IllegalArgumentException("Invalid verification table");
        }
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }

    private record ShardReport(int shard, long sourceCount, long targetCount, long missing) {
    }
}
