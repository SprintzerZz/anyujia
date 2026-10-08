package com.anyujia.anyujiaadminservice.user.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import com.anyujia.anyujiaadminapi.app.user.domain.dto.AppUserEditReqDTO;
import com.anyujia.anyujiaadminapi.app.user.domain.dto.AppUserListReqDTO;
import com.anyujia.anyujiaadminapi.app.user.domain.vo.AppUserVO;
import com.anyujia.anyujiaadminservice.user.domain.entity.AppUser;
import com.anyujia.anyujiaadminservice.user.mapper.AppUserMapper;
import com.anyujia.anyujiaadminservice.user.service.IAppUserService;
import com.anyujia.anyujiaadminservice.user.service.IAppUserIdentityService;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * C 端用户服务实现类。
 */
@Slf4j
@Service
@RefreshScope
@RequiredArgsConstructor
public class AppUserServiceImpl implements IAppUserService {

    /**
     * 默认启用状态。
     */
    private static final int STATUS_ENABLE = 1;

    /**
     * C 端用户 Mapper。
     */
    private final AppUserMapper appUserMapper;
    private final IAppUserIdentityService appUserIdentityService;

    /**
     * C 端用户默认头像。
     */
    @Value("${app.user.default-avatar:${appuser.info.defaultAvatar:}}")
    private String defaultAvatar;

    /**
     * 根据微信 openId 注册用户。
     *
     * @param openId 微信 openId
     * @return C 端用户信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUserVO registerByOpenId(String openId) {
        if (!StringUtils.hasText(openId)) {
            throw new ServiceException("微信openId不能为空", ResultCode.INVALID_PARA.getCode());
        }

        AppUser existUser = appUserMapper.selectByOpenId(openId);
        if (existUser != null) {
            appUserIdentityService.ensureTenantIdentity(existUser.getId());
            return toAppUserVO(existUser);
        }

        AppUser appUser = new AppUser();
        appUser.setOpenId(openId);
        appUser.setNickname(buildDefaultNickname());
        appUser.setAvatarUrl(defaultAvatar);
        appUser.setStatus(STATUS_ENABLE);
        appUserMapper.insert(appUser);
        appUserIdentityService.ensureTenantIdentity(appUser.getId());
        return toAppUserVO(appUser);
    }

    /**
     * 根据微信 openId 查询用户。
     *
     * @param openId 微信 openId
     * @return C 端用户信息，不存在时返回 {@code null}
     */
    @Override
    public AppUserVO findByOpenId(String openId) {
        if (!StringUtils.hasText(openId)) {
            return null;
        }
        return toAppUserVO(appUserMapper.selectByOpenId(openId));
    }

    /**
     * 根据手机号查询用户。
     *
     * @param phoneNumber 手机号
     * @return C 端用户信息，不存在时返回 {@code null}
     */
    @Override
    public AppUserVO findByPhoneNumber(String phoneNumber) {
        if (!StringUtils.hasText(phoneNumber)) {
            return null;
        }
        return toAppUserVO(appUserMapper.selectByPhoneNumber(phoneNumber));
    }

    /**
     * 根据手机号注册用户。
     *
     * @param phoneNumber 手机号
     * @return C 端用户信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUserVO registerByPhoneNumber(String phoneNumber) {
        if (!StringUtils.hasText(phoneNumber)) {
            throw new ServiceException("手机号不能为空", ResultCode.INVALID_PARA.getCode());
        }

        AppUser existUser = appUserMapper.selectByPhoneNumber(phoneNumber);
        if (existUser != null) {
            appUserIdentityService.ensureTenantIdentity(existUser.getId());
            return toAppUserVO(existUser);
        }

        AppUser appUser = new AppUser();
        appUser.setPhoneNumber(phoneNumber);
        appUser.setNickname(buildDefaultNickname());
        appUser.setAvatarUrl(defaultAvatar);
        appUser.setStatus(STATUS_ENABLE);
        appUserMapper.insert(appUser);
        appUserIdentityService.ensureTenantIdentity(appUser.getId());
        return toAppUserVO(appUser);
    }

    /**
     * 编辑 C 端用户。
     *
     * @param appUserEditReqDTO C 端用户编辑请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void edit(AppUserEditReqDTO appUserEditReqDTO) {
        AppUser appUser = appUserMapper.selectById(appUserEditReqDTO.getId());
        if (appUser == null) {
            throw new ServiceException("用户不存在", ResultCode.INVALID_PARA.getCode());
        }

        if (StringUtils.hasText(appUserEditReqDTO.getOpenId())) {
            appUser.setOpenId(appUserEditReqDTO.getOpenId());
        }
        if (StringUtils.hasText(appUserEditReqDTO.getPhoneNumber())) {
            appUser.setPhoneNumber(appUserEditReqDTO.getPhoneNumber());
        }
        if (StringUtils.hasText(appUserEditReqDTO.getNickname())) {
            appUser.setNickname(appUserEditReqDTO.getNickname());
        }
        if (StringUtils.hasText(appUserEditReqDTO.getAvatarUrl())) {
            appUser.setAvatarUrl(appUserEditReqDTO.getAvatarUrl());
        }
        if (appUserEditReqDTO.getGender() != null) {
            appUser.setGender(appUserEditReqDTO.getGender());
        }
        if (appUserEditReqDTO.getStatus() != null) {
            appUser.setStatus(appUserEditReqDTO.getStatus());
        }
        appUserMapper.updateById(appUser);
    }

    /**
     * 分页查询 C 端用户。
     *
     * @param appUserListReqDTO C 端用户分页查询请求
     * @return C 端用户分页信息
     */
    @Override
    public BasePageVO<AppUserVO> listPage(AppUserListReqDTO appUserListReqDTO) {
        BasePageVO<AppUserVO> result = new BasePageVO<>();
        int pageIndex = getPageIndex(appUserListReqDTO.getPageIndex());
        int pageSize = getPageSize(appUserListReqDTO.getPageSize());
        appUserListReqDTO.setPageIndex(pageIndex);
        appUserListReqDTO.setPageSize(pageSize);

        Long total = appUserMapper.selectCount(appUserListReqDTO);
        result.setPageIndex(pageIndex);
        result.setPageSize(pageSize);
        result.setTotal(total);
        if (total == null || total == 0L) {
            result.setList(new ArrayList<>());
            return result;
        }

        List<AppUser> appUserList = appUserMapper.selectPage(appUserListReqDTO);
        result.setList(appUserList.stream().map(this::toAppUserVO).collect(Collectors.toList()));
        return result;
    }

    /**
     * 根据用户 ID 查询用户。
     *
     * @param userId 用户 ID
     * @return C 端用户信息，不存在时返回 {@code null}
     */
    @Override
    public AppUserVO getById(Long userId) {
        if (userId == null) {
            return null;
        }
        return toAppUserVO(appUserMapper.selectById(userId));
    }

    /**
     * 根据用户 ID 列表批量查询用户。
     *
     * @param userIds 用户 ID 列表
     * @return C 端用户列表
     */
    @Override
    public List<AppUserVO> listByIds(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        return appUserMapper.selectBatchIds(userIds)
                .stream()
                .map(this::toAppUserVO)
                .collect(Collectors.toList());
    }

    /**
     * 转换 C 端用户响应对象。
     *
     * @param appUser C 端用户实体
     * @return C 端用户响应对象
     */
    private AppUserVO toAppUserVO(AppUser appUser) {
        if (appUser == null) {
            return null;
        }
        AppUserVO appUserVO = new AppUserVO();
        BeanUtils.copyProperties(appUser, appUserVO);
        return appUserVO;
    }

    /**
     * 构造默认用户昵称。
     *
     * @return 默认用户昵称
     */
    private String buildDefaultNickname() {
        return "安寓家用户" + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    /**
     * 获取安全页码。
     *
     * @param pageIndex 请求页码
     * @return 安全页码
     */
    private int getPageIndex(Integer pageIndex) {
        return pageIndex == null || pageIndex < 1 ? 1 : pageIndex;
    }

    /**
     * 获取安全每页数量。
     *
     * @param pageSize 请求每页数量
     * @return 安全每页数量
     */
    private int getPageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : pageSize;
    }
}
