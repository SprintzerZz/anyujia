package com.anyujia.anyujiaadminservice.user.controller;

import com.anyujia.anyujiaadminapi.app.user.domain.dto.AppUserEditReqDTO;
import com.anyujia.anyujiaadminapi.app.user.domain.vo.AppUserVO;
import com.anyujia.anyujiaadminapi.app.user.feign.AppUserFeignClient;
import com.anyujia.anyujiaadminservice.user.service.IAppUserService;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/app_user")
@RequiredArgsConstructor
public class AppUserController implements AppUserFeignClient {
    private final IAppUserService appUserService;

    @Override
    public ApiResult<AppUserVO> registerByOpenId(String openId) {
        return ApiResult.success(appUserService.registerByOpenId(openId));
    }

    @Override
    public ApiResult<AppUserVO> findByOpenId(String openId) {
        return ApiResult.success(appUserService.findByOpenId(openId));
    }

    @Override
    public ApiResult<AppUserVO> findByPhoneNumber(String phoneNumber) {
        return ApiResult.success(appUserService.findByPhoneNumber(phoneNumber));
    }

    @Override
    public ApiResult<AppUserVO> registerByPhoneNumber(String phoneNumber) {
        return ApiResult.success(appUserService.registerByPhoneNumber(phoneNumber));
    }

    @Override
    public ApiResult<Void> edit(@RequestBody @Valid AppUserEditReqDTO request) {
        appUserService.edit(request);
        return ApiResult.success();
    }

    @Override
    public ApiResult<AppUserVO> getById(Long userId) {
        return ApiResult.success(appUserService.getById(userId));
    }

    @Override
    public ApiResult<List<AppUserVO>> listByIds(@RequestBody List<Long> userIds) {
        return ApiResult.success(appUserService.listByIds(userIds));
    }
}
