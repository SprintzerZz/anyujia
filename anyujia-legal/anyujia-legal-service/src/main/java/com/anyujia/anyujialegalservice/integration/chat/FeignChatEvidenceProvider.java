package com.anyujia.anyujialegalservice.integration.chat;

import com.anyujia.anyujiachatapi.domain.dto.MessageListReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiachatapi.feign.ChatFeignClient;
import com.anyujia.anyujialegalservice.service.ChatEvidenceProvider;
import com.anyujia.anyujialegalservice.service.LegalShortTermMemoryService;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeignChatEvidenceProvider implements ChatEvidenceProvider {
    private final ChatFeignClient chatFeignClient;
    private final LegalShortTermMemoryService memoryService;

    @Override
    public List<MessageVO> recentMessages(Long tenantId, Long conversationId, int limit) {
        int boundedLimit = memoryService.normalizeLimit(limit);
        var cached = memoryService.getChatWindow(tenantId, conversationId, boundedLimit);
        if (cached.isPresent()) {
            return cached.get();
        }
        try {
            MessageListReqDTO request = new MessageListReqDTO();
            request.setSessionId(conversationId);
            request.setCount(boundedLimit);
            ApiResult<List<MessageVO>> response = chatFeignClient.messages(tenantId, "app", request);
            if (response == null || response.getCode() != ResultCode.SUCCESS.getCode() || response.getData() == null) {
                return List.of();
            }
            List<MessageVO> messages = response.getData();
            memoryService.putChatWindow(tenantId, conversationId, boundedLimit, messages);
            return messages;
        } catch (Exception ex) {
            log.warn("Chat evidence unavailable for conversation {}: {}", conversationId, ex.getMessage());
            return List.of();
        }
    }
}
