package com.anyujia.anyujiachatservice.cache;

import static org.assertj.core.api.Assertions.assertThat;
import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiacommonredis.service.RedisService;
import com.anyujia.anyujiacommonredis.service.RedissonLockService;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ChatRecentMessageCacheTest {
    @Test
    void cacheHitDoesNotLoadDatabase() {
        StubRedisService redisService = new StubRedisService();
        RedissonLockService lockService = null;
        ChatRecentMessageCache cache = new ChatRecentMessageCache(redisService, lockService);
        MessageVO message = message("8");
        redisService.state = "POPULATED";
        redisService.messages.add(message);
        AtomicBoolean databaseLoaded = new AtomicBoolean();

        List<MessageVO> result = cache.getRecent(7L, 20, () -> {
            databaseLoaded.set(true);
            return List.of();
        });

        assertThat(result).containsExactly(message);
        assertThat(databaseLoaded).isFalse();
    }

    @Test
    void appendDoesNotCreatePartialCacheForColdConversation() {
        StubRedisService redisService = new StubRedisService();
        RedissonLockService lockService = null;
        ChatRecentMessageCache cache = new ChatRecentMessageCache(redisService, lockService);

        cache.append(message("8"));

        assertThat(redisService.appendCount).isZero();
    }

    private MessageVO message(String sequence) {
        MessageVO message = new MessageVO();
        message.setMessageId(sequence);
        message.setSessionId(7L);
        message.setConversationSeq(sequence);
        return message;
    }

    private static final class StubRedisService extends RedisService {
        private String state;
        private int appendCount;
        private final LinkedHashSet<MessageVO> messages = new LinkedHashSet<>();

        private StubRedisService() {
            super(null);
        }

        @Override
        public Boolean hasKey(String key) {
            return !messages.isEmpty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getCacheObject(String key, Class<T> clazz) {
            return (T) state;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Set<T> getCacheZSetByRange(String key, long start, long end,
                                              TypeReference<LinkedHashSet<T>> typeReference) {
            return (Set<T>) messages;
        }

        @Override
        public void addMemberZSet(String key, Object value, double seqNo) {
            appendCount++;
        }
    }
}
