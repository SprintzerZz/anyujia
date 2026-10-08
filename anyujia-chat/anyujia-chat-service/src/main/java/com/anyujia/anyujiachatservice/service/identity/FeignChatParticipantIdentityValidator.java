package com.anyujia.anyujiachatservice.service.identity;

import com.anyujia.anyujiaadminapi.app.user.domain.dto.ChatParticipantIdentityReqDTO;
import com.anyujia.anyujiaadminapi.app.user.domain.vo.ChatParticipantIdentityVO;
import com.anyujia.anyujiaadminapi.app.user.feign.AppUserIdentityFeignClient;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeignChatParticipantIdentityValidator implements ChatParticipantIdentityValidator {
    private final AppUserIdentityFeignClient identityFeignClient;

    @Override
    public void validate(Long tenantUserId, Long landlordUserId) {
        ChatParticipantIdentityReqDTO request = new ChatParticipantIdentityReqDTO();
        request.setTenantUserId(tenantUserId);
        request.setLandlordUserId(landlordUserId);
        ApiResult<ChatParticipantIdentityVO> response = identityFeignClient.checkChatParticipants(request);
        if (response == null || response.getCode() != ResultCode.SUCCESS.getCode() || response.getData() == null) {
            throw new ServiceException("Failed to validate chat participant identities");
        }
        ChatParticipantIdentityVO identity = response.getData();
        if (!identity.isTenantActive()) {
            throw invalid("Tenant identity is missing or disabled");
        }
        if (!identity.isLandlordActive()) {
            throw invalid("Landlord identity is missing or disabled");
        }
        if (!identity.isLandlordVerified()) {
            throw invalid("Landlord identity is not verified");
        }
    }

    private ServiceException invalid(String message) {
        return new ServiceException(message, ResultCode.INVALID_PARA.getCode());
    }
}
