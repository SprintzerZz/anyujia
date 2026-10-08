package com.anyujia.anyujiaadminservice.user.controller;

import com.anyujia.anyujiaadminapi.app.user.domain.dto.ChatParticipantIdentityReqDTO;
import com.anyujia.anyujiaadminapi.app.user.domain.vo.ChatParticipantIdentityVO;
import com.anyujia.anyujiaadminapi.app.user.feign.AppUserIdentityFeignClient;
import com.anyujia.anyujiaadminservice.user.service.IAppUserIdentityService;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/app_user/identity")
@RequiredArgsConstructor
public class AppUserIdentityController implements AppUserIdentityFeignClient {
    private final IAppUserIdentityService identityService;

    @Override
    public ApiResult<ChatParticipantIdentityVO> checkChatParticipants(
            @RequestBody @Valid ChatParticipantIdentityReqDTO request) {
        return ApiResult.success(identityService.checkChatParticipants(request));
    }
}
