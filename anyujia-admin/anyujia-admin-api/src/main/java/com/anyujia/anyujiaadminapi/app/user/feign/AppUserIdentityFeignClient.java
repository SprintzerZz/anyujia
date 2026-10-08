package com.anyujia.anyujiaadminapi.app.user.feign;

import com.anyujia.anyujiaadminapi.app.user.domain.dto.ChatParticipantIdentityReqDTO;
import com.anyujia.anyujiaadminapi.app.user.domain.vo.ChatParticipantIdentityVO;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(contextId = "appUserIdentityFeignClient", value = "anyujia-admin", path = "/app_user/identity")
public interface AppUserIdentityFeignClient {
    @PostMapping("/chat/check")
    ApiResult<ChatParticipantIdentityVO> checkChatParticipants(
            @RequestBody @Valid ChatParticipantIdentityReqDTO request);
}
