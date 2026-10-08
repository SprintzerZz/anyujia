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

    @Override
    public Optional<HousePublishVO> snapshot(Long tenantId, Long conversationId) {
        try {
            ConversationContextReqDTO request = new ConversationContextReqDTO();
            request.setSessionId(conversationId);
            ApiResult<ConversationContextVO> contextResponse = chatFeignClient.context("service", request);
            if (contextResponse == null || contextResponse.getCode() != ResultCode.SUCCESS.getCode()
                    || contextResponse.getData() == null) {
                return Optional.empty();
            }
            ConversationContextVO context = contextResponse.getData();
            if (!tenantId.equals(context.getTenantUserId()) || context.getHousePublishId() == null) {
                return Optional.empty();
            }
            ApiResult<HousePublishVO> houseResponse = housePublishFeignClient.getSnapshot(context.getHousePublishId());
            if (houseResponse == null || houseResponse.getCode() != ResultCode.SUCCESS.getCode()
                    || houseResponse.getData() == null) {
                return Optional.empty();
            }
            return Optional.of(houseResponse.getData());
        } catch (Exception ex) {
            log.warn("Rental product evidence unavailable for conversation {}: {}", conversationId, ex.getMessage());
            return Optional.empty();
        }
    }
}
