package com.anyujia.anyujialegalservice.integration.chat;

import com.anyujia.anyujiachatapi.domain.dto.MessageListReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiachatapi.feign.ChatFeignClient;
import com.anyujia.anyujialegalservice.service.ChatEvidenceProvider;
import com.anyujia.anyujialegalservice.service.LegalShortTermMemoryService;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FeignChatEvidenceProvider implements ChatEvidenceProvider {
    private final ChatFeignClient chatFeignClient;
    private final com.anyujia.anyujialegalservice.service.LegalResilientCallExecutor resilientCallExecutor;
    private final LegalShortTermMemoryService memoryService;

    @Autowired
    public FeignChatEvidenceProvider(ChatFeignClient chatFeignClient,
                                     com.anyujia.anyujialegalservice.service.LegalResilientCallExecutor resilientCallExecutor,
                                     LegalShortTermMemoryService memoryService) {
        this.chatFeignClient = chatFeignClient;
        this.resilientCallExecutor = resilientCallExecutor;
        this.memoryService = memoryService;
    }

    public FeignChatEvidenceProvider(ChatFeignClient chatFeignClient, LegalShortTermMemoryService memoryService) {
        this(chatFeignClient, new com.anyujia.anyujialegalservice.service.LegalResilientCallExecutor(), memoryService);
    }

    @Override
    public List<MessageVO> recentMessages(Long tenantId, Long conversationId, int limit) {
        int boundedLimit = memoryService.normalizeLimit(limit);
        var cached = memoryService.getChatWindow(tenantId, conversationId, boundedLimit);
        if (cached.isPresent()) {
            return cached.get();
        }
        return freshMessages(tenantId, conversationId, boundedLimit);
    }

    @Override
    public List<MessageVO> freshMessages(Long tenantId, Long conversationId, int limit) {
        int boundedLimit = memoryService.normalizeLimit(limit);
        try {
            MessageListReqDTO request = new MessageListReqDTO();
            request.setSessionId(conversationId);
            request.setCount(boundedLimit);
            ApiResult<List<MessageVO>> response = resilientCallExecutor.execute("chat",
                    () -> chatFeignClient.messages(tenantId, "app", request), () -> null);
            if (response == null || response.getCode() != ResultCode.SUCCESS.getCode() || response.getData() == null) {
                throw new IllegalStateException("Chat evidence response unavailable");
            }
            List<MessageVO> messages = response.getData();
            memoryService.putChatWindow(tenantId, conversationId, boundedLimit, messages);
            return messages;
        } catch (Exception ex) {
            log.warn("Chat evidence unavailable for conversation {}: {}", conversationId, ex.getMessage());
            throw new IllegalStateException("Chat evidence unavailable for consistency check", ex);
        }
    }
}
