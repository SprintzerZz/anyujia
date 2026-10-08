package com.anyujia.anyujialegalservice.integration.chat;

import com.anyujia.anyujiachatapi.domain.dto.MessageListReqDTO;
import com.anyujia.anyujiachatapi.feign.ChatFeignClient;
import com.anyujia.anyujialegalservice.service.ConversationAccessVerifier;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeignConversationAccessVerifier implements ConversationAccessVerifier {
    private final ChatFeignClient chatFeignClient;

    @Override
    public void requireTenantAccess(Long tenantId, Long conversationId) {
        MessageListReqDTO request = new MessageListReqDTO();
        request.setSessionId(conversationId);
        request.setCount(1);
        ApiResult<?> response = chatFeignClient.messages(tenantId, "app", request);
        if (response == null || response.getCode() != ResultCode.SUCCESS.getCode()) {
            throw new ServiceException("无权访问该租赁会话", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
    }
}
