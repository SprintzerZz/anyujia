package com.anyujia.anyujiaadminservice.user.service;

import java.util.List;

import com.anyujia.anyujiaadminservice.user.domain.dto.PasswordLoginDTO;
import com.anyujia.anyujiaadminservice.user.domain.dto.SysUserDTO;
import com.anyujia.anyujiaadminservice.user.domain.dto.SysUserListReqDTO;
import com.anyujia.anyujiaadminservice.user.domain.dto.SysUserLoginDTO;
import com.anyujia.anyujiacommonsecurity.domain.dto.TokenDTO;

/**
 * B 端系统用户服务接口。
 */
public interface ISysUserService {

    /**
     * 账号密码登录。
     *
     * @param passwordLoginDTO 登录请求
     * @return 令牌信息
     */
    TokenDTO login(PasswordLoginDTO passwordLoginDTO);

    /**
     * 新增或编辑用户。
     *
     * @param sysUserDTO 用户信息
     * @return 用户主键 ID
     */
    Long addOrEdit(SysUserDTO sysUserDTO);

    /**
     * 查询用户列表。
     *
     * @param sysUserListReqDTO 用户查询条件
     * @return 用户列表
     */
    List<SysUserDTO> getUserList(SysUserListReqDTO sysUserListReqDTO);

    /**
     * 获取当前登录用户信息。
     *
     * @return 登录用户信息
     */
    SysUserLoginDTO getLoginUser();
}
