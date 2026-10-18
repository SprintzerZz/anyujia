package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

/**
 * Tracks per-recipient delivery acknowledgements so undelivered messages can be re-pushed.
 *
 * <p>When a message is pushed to an online connection the consumer registers a pending ack
 * with a deadline. The client confirms receipt with a {@code delivery_ack} frame. Entries whose
 * deadline passes are scanned by {@link ChatDeliveryReaper} and retried or dead-lettered.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatDeliveryAckService {
    private static final String DUE_ZSET = "chat:delivery:due";
    private static final String PENDING_PREFIX = "chat:delivery:pending:";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final StringCodec codec = StringCodec.INSTANCE;

    public void track(Long userId, MessageVO message, int attempts, long ackTimeoutMillis) {
        try {
            PendingDelivery pending = new PendingDelivery(userId, message, attempts);
            redissonClient.getMap(PENDING_PREFIX + userId, codec)
                    .put(message.getMessageId(), objectMapper.writeValueAsString(pending));
            redissonClient.getScoredSortedSet(DUE_ZSET, codec)
                    .add(System.currentTimeMillis() + ackTimeoutMillis, member(userId, message.getMessageId()));
        } catch (Exception ex) {
            log.warn("Failed to track pending delivery, userId={}, messageId={}",
                    userId, message.getMessageId(), ex);
        }
    }

    public void ack(Long userId, String messageId) {
        try {
            redissonClient.getMap(PENDING_PREFIX + userId, codec).remove(messageId);
            redissonClient.getScoredSortedSet(DUE_ZSET, codec).remove(member(userId, messageId));
        } catch (Exception ex) {
            log.warn("Failed to acknowledge delivery, userId={}, messageId={}", userId, messageId, ex);
        }
    }

    public void remove(Long userId, String messageId) {
        try {
            redissonClient.getMap(PENDING_PREFIX + userId, codec).remove(messageId);
            redissonClient.getScoredSortedSet(DUE_ZSET, codec).remove(member(userId, messageId));
        } catch (Exception ex) {
            log.warn("Failed to remove pending delivery, userId={}, messageId={}", userId, messageId, ex);
        }
    }

    public List<PendingDelivery> overdue(long nowMillis, int limit) {
        List<PendingDelivery> result = new ArrayList<>();
        try {
            RScoredSortedSet<String> dueSet = redissonClient.getScoredSortedSet(DUE_ZSET, StringCodec.INSTANCE);
            Collection<String> members = dueSet.valueRange(0, true, nowMillis, true);
            for (String entry : members) {
                if (result.size() >= limit) {
                    break;
                }
                String[] parts = splitMember(entry);
                if (parts == null) {
                    continue;
                }
                RMap<String, String> pendingMap = redissonClient.getMap(PENDING_PREFIX + parts[0], StringCodec.INSTANCE);
                String json = pendingMap.get(parts[1]);
                if (json != null) {
                    result.add(objectMapper.readValue(json, PendingDelivery.class));
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to scan overdue deliveries", ex);
        }
        return result;
    }

    private String member(Long userId, String messageId) {
        return userId + ":" + messageId;
    }

    private String[] splitMember(String member) {
        int idx = member.indexOf(':');
        if (idx <= 0 || idx == member.length() - 1) {
            return null;
        }
        return new String[] { member.substring(0, idx), member.substring(idx + 1) };
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingDelivery {
        private Long recipientId;
        private MessageVO message;
        private int attempts;
    }
}
