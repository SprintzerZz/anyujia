package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujialegalservice.config.LegalMemoryProperties;
import com.anyujia.anyujialegalservice.domain.model.ShortTermChatMemory;
import com.anyujia.anyujiacommonredis.service.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class LegalShortTermMemoryServiceTest {
    @Test
    void storesAndReadsChatWindowFromRedis() {
        FakeRedisService redisService = new FakeRedisService();
        ObjectProvider<RedisService> provider = provider(redisService);
        LegalMemoryProperties properties = new LegalMemoryProperties();
        LegalShortTermMemoryService service = new LegalShortTermMemoryService(properties, provider);
        MessageVO message = message("m-1");
        redisService.object = memory(message);

        var cached = service.getChatWindow(7L, 9L, 100);
        service.putChatWindow(7L, 9L, 100, List.of(message));

        assertThat(cached).isPresent();
        assertThat(cached.orElseThrow()).singleElement().extracting(MessageVO::getMessageId).isEqualTo("m-1");
        assertThat(redisService.writtenKey).isEqualTo("legal:memory:chat:7:9:100");
        assertThat(redisService.timeout).isEqualTo(1800L);
        assertThat(redisService.timeUnit).isEqualTo(TimeUnit.SECONDS);
    }

    @Test
    void disablesRedisAfterFailureAndFallsBackToEmpty() {
        FakeRedisService redisService = new FakeRedisService();
        redisService.failReads = true;
        LegalShortTermMemoryService service =
                new LegalShortTermMemoryService(new LegalMemoryProperties(), provider(redisService));

        var first = service.getChatWindow(7L, 9L, 100);
        service.putChatWindow(7L, 9L, 100, List.of(message("m-1")));
        var second = service.getChatWindow(7L, 9L, 100);

        assertThat(first).isEmpty();
        assertThat(second).isEmpty();
    }

    @Test
    void normalizesLimitByConfiguredWindow() {
        LegalMemoryProperties properties = new LegalMemoryProperties();
        properties.setChatWindowLimit(20);
        LegalShortTermMemoryService service = new LegalShortTermMemoryService(properties, provider(null));

        assertThat(service.normalizeLimit(0)).isEqualTo(1);
        assertThat(service.normalizeLimit(50)).isEqualTo(20);
    }

    private ShortTermChatMemory memory(MessageVO message) {
        ShortTermChatMemory memory = new ShortTermChatMemory();
        memory.setMessages(List.of(message));
        memory.setMessageCount(1);
        return memory;
    }

    private MessageVO message(String id) {
        MessageVO message = new MessageVO();
        message.setMessageId(id);
        message.setContent("押金不能退");
        return message;
    }

    private ObjectProvider<RedisService> provider(RedisService redisService) {
        return new ObjectProvider<>() {
            @Override
            public RedisService getObject(Object... args) {
                return redisService;
            }

            @Override
            public RedisService getIfAvailable() {
                return redisService;
            }

            @Override
            public RedisService getIfUnique() {
                return redisService;
            }

            @Override
            public RedisService getObject() {
                return redisService;
            }
        };
    }

    private static class FakeRedisService extends RedisService {
        private ShortTermChatMemory object;
        private boolean failReads;
        private String writtenKey;
        private Long timeout;
        private TimeUnit timeUnit;

        FakeRedisService() {
            super(null);
        }

        @Override
        public <T> T getCacheObject(String key, Class<T> clazz) {
            if (failReads) {
                throw new IllegalStateException("Redis unavailable");
            }
            return clazz.cast(object);
        }

        @Override
        public <T> void setCacheObject(String key, T value, Long timeout, TimeUnit timeUnit) {
            this.writtenKey = key;
            this.object = (ShortTermChatMemory) value;
            this.timeout = timeout;
            this.timeUnit = timeUnit;
        }

        @Override
        public Collection<String> keys(String pattern) {
            return Collections.emptySet();
        }

        @Override
        public boolean deleteObject(Collection<String> collection) {
            return !collection.isEmpty();
        }
    }
}
