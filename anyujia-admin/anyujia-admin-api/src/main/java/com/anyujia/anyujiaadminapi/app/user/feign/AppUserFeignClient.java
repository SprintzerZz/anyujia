package com.anyujia.anyujiaadminapi.app.user.feign;

import java.util.List;

import com.anyujia.anyujiaadminapi.app.user.domain.dto.AppUserEditReqDTO;
import com.anyujia.anyujiaadminapi.app.user.domain.vo.AppUserVO;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * C 端用户数据远程调用客户端。
 */
@FeignClient(contextId = "appUserFeignClient", value = "anyujia-admin", path = "/app_user")
public interface AppUserFeignClient {

    /**
     * 根据微信 openId 注册用户。
     *
     * @param openId 微信 openId
     * @return C 端用户信息
     */
    @GetMapping("/register/openid")
    ApiResult<AppUserVO> registerByOpenId(@RequestParam("openId") String openId);

    /**
     * 根据微信 openId 查询用户信息。
     *
     * @param openId 微信 openId
     * @return C 端用户信息
     */
    @GetMapping("/open_id_find")
    ApiResult<AppUserVO> findByOpenId(@RequestParam("openId") String openId);

    /**
     * 根据手机号查询用户信息。
     *
     * @param phoneNumber 手机号
     * @return C 端用户信息
     */
    @GetMapping("/phone_find")
    ApiResult<AppUserVO> findByPhoneNumber(@RequestParam("phoneNumber") String phoneNumber);

    /**
     * 根据手机号注册用户。
     *
     * @param phoneNumber 手机号
     * @return C 端用户信息
     */
    @GetMapping("/register/phone")
    ApiResult<AppUserVO> registerByPhoneNumber(@RequestParam("phoneNumber") String phoneNumber);

    /**
     * 编辑 C 端用户信息。
     *
     * @param appUserEditReqDTO C 端用户编辑请求
     * @return 空响应
     */
    @PostMapping("/edit")
    ApiResult<Void> edit(@RequestBody @Validated AppUserEditReqDTO appUserEditReqDTO);

    /**
     * 根据用户 ID 查询用户信息。
     *
     * @param userId 用户 ID
     * @return C 端用户信息
     */
    @GetMapping("/id_find")
    ApiResult<AppUserVO> getById(@RequestParam("userId") Long userId);

    /**
     * 根据用户 ID 列表批量查询用户信息。
     *
     * @param userIds 用户 ID 列表
     * @return C 端用户列表
     */
    @PostMapping("/list")
    ApiResult<List<AppUserVO>> listByIds(@RequestBody List<Long> userIds);
}
