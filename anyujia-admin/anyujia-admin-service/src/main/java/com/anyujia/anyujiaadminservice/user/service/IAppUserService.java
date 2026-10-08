package com.anyujia.anyujiaadminservice.user.service;

import java.util.List;

import com.anyujia.anyujiaadminapi.app.user.domain.dto.AppUserEditReqDTO;
import com.anyujia.anyujiaadminapi.app.user.domain.dto.AppUserListReqDTO;
import com.anyujia.anyujiaadminapi.app.user.domain.vo.AppUserVO;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;

/**
 * C 端用户服务接口。
 */
public interface IAppUserService {

    /**
     * 根据微信 openId 注册用户。
     *
     * @param openId 微信 openId
     * @return C 端用户信息
     */
    AppUserVO registerByOpenId(String openId);

    /**
     * 根据微信 openId 查询用户。
     *
     * @param openId 微信 openId
     * @return C 端用户信息，不存在时返回 {@code null}
     */
    AppUserVO findByOpenId(String openId);

    /**
     * 根据手机号查询用户。
     *
     * @param phoneNumber 手机号
     * @return C 端用户信息，不存在时返回 {@code null}
     */
    AppUserVO findByPhoneNumber(String phoneNumber);

    /**
     * 根据手机号注册用户。
     *
     * @param phoneNumber 手机号
     * @return C 端用户信息
     */
    AppUserVO registerByPhoneNumber(String phoneNumber);

    /**
     * 编辑 C 端用户。
     *
     * @param appUserEditReqDTO C 端用户编辑请求
     */
    void edit(AppUserEditReqDTO appUserEditReqDTO);

    /**
     * 分页查询 C 端用户。
     *
     * @param appUserListReqDTO C 端用户分页查询请求
     * @return C 端用户分页信息
     */
    BasePageVO<AppUserVO> listPage(AppUserListReqDTO appUserListReqDTO);

    /**
     * 根据用户 ID 查询用户。
     *
     * @param userId 用户 ID
     * @return C 端用户信息，不存在时返回 {@code null}
     */
    AppUserVO getById(Long userId);

    /**
     * 根据用户 ID 列表批量查询用户。
     *
     * @param userIds 用户 ID 列表
     * @return C 端用户列表
     */
    List<AppUserVO> listByIds(List<Long> userIds);
}
