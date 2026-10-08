package com.anyujia.anyujialegalservice.integration.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anyujia.anyujiachatapi.domain.dto.MessageListReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiachatapi.feign.ChatFeignClient;
import com.anyujia.anyujialegalservice.config.LegalMemoryProperties;
import com.anyujia.anyujialegalservice.service.LegalShortTermMemoryService;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FeignChatEvidenceProviderTest {
    @Test
    void returnsCachedChatWindowWithoutFeignCall() {
        ChatFeignClient chatFeignClient = mock(ChatFeignClient.class);
        MessageVO cached = message("cached");
        FakeMemoryService memoryService = new FakeMemoryService(Optional.of(List.of(cached)));
        FeignChatEvidenceProvider provider = new FeignChatEvidenceProvider(chatFeignClient, memoryService);

        var result = provider.recentMessages(1L, 2L, 100);

        assertThat(result).singleElement().extracting(MessageVO::getMessageId).isEqualTo("cached");
        verify(chatFeignClient, never()).messages(any(), any(), any());
    }

    @Test
    void writesFetchedMessagesToShortTermMemory() {
        ChatFeignClient chatFeignClient = mock(ChatFeignClient.class);
        FakeMemoryService memoryService = new FakeMemoryService(Optional.empty());
        MessageVO fetched = message("fetched");
        ApiResult<List<MessageVO>> response = ApiResult.success(List.of(fetched));
        when(chatFeignClient.messages(any(), any(), any(MessageListReqDTO.class))).thenReturn(response);
        FeignChatEvidenceProvider provider = new FeignChatEvidenceProvider(chatFeignClient, memoryService);

        var result = provider.recentMessages(1L, 2L, 500);

        assertThat(result).singleElement().extracting(MessageVO::getMessageId).isEqualTo("fetched");
        assertThat(memoryService.writtenMessages).containsExactly(fetched);
        assertThat(memoryService.writtenLimit).isEqualTo(100);
    }

    private MessageVO message(String id) {
        MessageVO message = new MessageVO();
        message.setMessageId(id);
        message.setContent("押金不能退");
        return message;
    }

    private static class FakeMemoryService extends LegalShortTermMemoryService {
        private final Optional<List<MessageVO>> cached;
        private List<MessageVO> writtenMessages = List.of();
        private int writtenLimit;

        FakeMemoryService(Optional<List<MessageVO>> cached) {
            super(new LegalMemoryProperties(), new org.springframework.beans.factory.ObjectProvider<>() {
                @Override
                public com.anyujia.anyujiacommonredis.service.RedisService getObject(Object... args) {
                    return null;
                }

                @Override
                public com.anyujia.anyujiacommonredis.service.RedisService getIfAvailable() {
                    return null;
                }

                @Override
                public com.anyujia.anyujiacommonredis.service.RedisService getIfUnique() {
                    return null;
                }

                @Override
                public com.anyujia.anyujiacommonredis.service.RedisService getObject() {
                    return null;
                }
            });
            this.cached = cached;
        }

        @Override
        public Optional<List<MessageVO>> getChatWindow(Long tenantId, Long conversationId, int limit) {
            return cached;
        }

        @Override
        public void putChatWindow(Long tenantId, Long conversationId, int limit, List<MessageVO> messages) {
            this.writtenLimit = limit;
            this.writtenMessages = messages;
        }

        @Override
        public int normalizeLimit(int requestedLimit) {
            return Math.max(1, Math.min(requestedLimit, 100));
        }
    }
}
