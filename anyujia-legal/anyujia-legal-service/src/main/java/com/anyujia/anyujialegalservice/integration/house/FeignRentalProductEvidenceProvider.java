package com.anyujia.anyujialegalservice.integration.house;

import com.anyujia.anyujiachatapi.domain.dto.ConversationContextReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.ConversationContextVO;
import com.anyujia.anyujiachatapi.feign.ChatFeignClient;
import com.anyujia.anyujiahouseapi.domain.vo.HousePublishVO;
import com.anyujia.anyujiahouseapi.feign.HousePublishFeignClient;
import com.anyujia.anyujialegalservice.service.RentalProductEvidenceProvider;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeignRentalProductEvidenceProvider implements RentalProductEvidenceProvider {
    private final ChatFeignClient chatFeignClient;
    private final HousePublishFeignClient housePublishFeignClient;
    private final com.anyujia.anyujialegalservice.service.LegalResilientCallExecutor resilientCallExecutor;

    @Override
    public Optional<HousePublishVO> snapshot(Long tenantId, Long conversationId) {
        try {
            ConversationContextReqDTO request = new ConversationContextReqDTO();
            request.setSessionId(conversationId);
            ApiResult<ConversationContextVO> contextResponse = resilientCallExecutor.execute("chat-context",
                    () -> chatFeignClient.context("service", request), () -> null);
            if (contextResponse == null || contextResponse.getCode() != ResultCode.SUCCESS.getCode()
                    || contextResponse.getData() == null) {
                throw new IllegalStateException("Chat conversation context unavailable");
            }
            ConversationContextVO context = contextResponse.getData();
            if (!tenantId.equals(context.getTenantUserId()) || context.getHousePublishId() == null) {
                return Optional.empty();
            }
            ApiResult<HousePublishVO> houseResponse = resilientCallExecutor.execute("house-snapshot",
                    () -> housePublishFeignClient.getSnapshot(context.getHousePublishId()), () -> null);
            if (houseResponse == null || houseResponse.getCode() != ResultCode.SUCCESS.getCode()
                    || houseResponse.getData() == null) {
                throw new IllegalStateException("Rental product snapshot unavailable");
            }
            return Optional.of(houseResponse.getData());
        } catch (Exception ex) {
            log.warn("Rental product evidence unavailable for conversation {}: {}", conversationId, ex.getMessage());
            throw new IllegalStateException("Rental product evidence unavailable for consistency check", ex);
        }
    }
}
