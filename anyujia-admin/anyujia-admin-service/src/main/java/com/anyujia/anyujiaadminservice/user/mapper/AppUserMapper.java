package com.anyujia.anyujiaadminservice.user.mapper;

import java.util.List;

import com.anyujia.anyujiaadminapi.app.user.domain.dto.AppUserListReqDTO;
import com.anyujia.anyujiaadminservice.user.domain.entity.AppUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * C 端用户表 Mapper。
 *
 * <p>负责 app_user 表的基础 CRUD 与自定义查询。</p>
 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {

    /**
     * 根据 openId 查询用户信息。
     *
     * @param openId 用户微信 openId
     * @return C 端用户
     */
    AppUser selectByOpenId(@Param("openId") String openId);

    /**
     * 根据手机号查询用户信息。
     *
     * @param phoneNumber 手机号
     * @return C 端用户
     */
    AppUser selectByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    /**
     * 查询用户总数。
     *
     * @param appUserListReqDTO 查询 C 端用户请求 DTO
     * @return 用户总数
     */
    Long selectCount(AppUserListReqDTO appUserListReqDTO);

    /**
     * 分页查询 C 端用户。
     *
     * @param appUserListReqDTO 查询 C 端用户请求 DTO
     * @return 用户列表
     */
    List<AppUser> selectPage(AppUserListReqDTO appUserListReqDTO);
}
