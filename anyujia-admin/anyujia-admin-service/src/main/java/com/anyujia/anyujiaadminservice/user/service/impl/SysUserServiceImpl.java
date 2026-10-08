package com.anyujia.anyujiaadminservice.user.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

import com.anyujia.anyujiaadminservice.config.service.ISysDictionaryService;
import com.anyujia.anyujiaadminservice.user.domain.dto.PasswordLoginDTO;
import com.anyujia.anyujiaadminservice.user.domain.dto.SysUserDTO;
import com.anyujia.anyujiaadminservice.user.domain.dto.SysUserListReqDTO;
import com.anyujia.anyujiaadminservice.user.domain.dto.SysUserLoginDTO;
import com.anyujia.anyujiaadminservice.user.domain.entity.SysUser;
import com.anyujia.anyujiaadminservice.user.mapper.SysUserMapper;
import com.anyujia.anyujiaadminservice.user.service.ISysUserService;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.anyujia.anyujiacommonsecurity.domain.dto.LoginUserDTO;
import com.anyujia.anyujiacommonsecurity.domain.dto.TokenDTO;
import com.anyujia.anyujiacommonsecurity.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * B 端用户登录和管理服务实现类。
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements ISysUserService {

    /**
     * 禁用状态字典键。
     */
    private static final String STATUS_DISABLE = "disable";

    /**
     * 用户来源。
     */
    private static final String USER_FROM_SYS = "sys";

    /**
     * B 端用户 Mapper。
     */
    private final SysUserMapper sysUserMapper;

    /**
     * Token 服务。
     */
    private final TokenService tokenService;

    /**
     * 字典服务。
     */
    private final ISysDictionaryService sysDictionaryService;

    /**
     * B 端用户账号密码登录。
     *
     * @param passwordLoginDTO 用户登录请求
     * @return token 信息
     */
    @Override
    public TokenDTO login(PasswordLoginDTO passwordLoginDTO) {
        // 1. 校验手机号格式。
        if (!checkPhone(passwordLoginDTO.getPhone())) {
            throw new ServiceException("手机号不合理", ResultCode.INVALID_PARA.getCode());
        }

        // 2. 根据手机号查询用户。
        SysUser sysUser = sysUserMapper.selectByPhoneNumber(passwordLoginDTO.getPhone());
        if (sysUser == null) {
            throw new ServiceException("手机号不存在", ResultCode.INVALID_PARA.getCode());
        }

        // 3. 校验密码。
        String password = passwordLoginDTO.getPassword();
        if (!StringUtils.hasText(password)) {
            throw new ServiceException("密码解析为空", ResultCode.INVALID_PARA.getCode());
        }
        String passwordEncrypt = sha256Hex(password);
        if (!passwordEncrypt.equals(sysUser.getPassword())) {
            throw new ServiceException("密码不正确", ResultCode.INVALID_PARA.getCode());
        }

        // 4. 校验用户状态。
        if (STATUS_DISABLE.equals(sysUser.getStatus())) {
            throw new ServiceException(ResultCode.USER_DISABLE);
        }

        // 5. 创建登录用户上下文并生成 token。
        LoginUserDTO loginUserDTO = new LoginUserDTO();
        loginUserDTO.setUserId(sysUser.getId());
        loginUserDTO.setUserName(sysUser.getNickName());
        loginUserDTO.setUserFrom(USER_FROM_SYS);
        return tokenService.createToken(loginUserDTO);
    }

    /**
     * 新增或编辑 B 端用户。
     *
     * @param sysUserDTO B 端用户信息
     * @return 用户主键 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addOrEdit(SysUserDTO sysUserDTO) {
        SysUser sysUser = new SysUser();

        // 1. 新增用户时校验手机号、密码、手机号唯一性和用户身份。
        if (sysUserDTO.getUserId() == null) {
            if (!checkPhone(sysUserDTO.getPhoneNumber())) {
                throw new ServiceException("手机格式错误", ResultCode.INVALID_PARA.getCode());
            }
            if (!StringUtils.hasText(sysUserDTO.getPassword()) || !sysUserDTO.checkPassword()) {
                throw new ServiceException("密码校验失败", ResultCode.INVALID_PARA.getCode());
            }
            SysUser existSysUser = sysUserMapper.selectByPhoneNumber(sysUserDTO.getPhoneNumber());
            if (existSysUser != null) {
                throw new ServiceException("手机号已经被占用", ResultCode.INVALID_PARA.getCode());
            }
            if (!StringUtils.hasText(sysUserDTO.getIdentity())
                    || sysDictionaryService.getDictDataByKey(sysUserDTO.getIdentity()) == null) {
                throw new ServiceException("用户身份错误", ResultCode.INVALID_PARA.getCode());
            }
            sysUser.setPhoneNumber(sysUserDTO.getPhoneNumber());
            sysUser.setPassword(sha256Hex(sysUserDTO.getPassword()));
            sysUser.setIdentity(sysUserDTO.getIdentity());
        } else {
            sysUser = sysUserMapper.selectById(sysUserDTO.getUserId());
            if (sysUser == null) {
                throw new ServiceException("用户不存在", ResultCode.INVALID_PARA.getCode());
            }
        }

        // 2. 新增和编辑都需要校验状态字典键。
        if (sysDictionaryService.getDictDataByKey(sysUserDTO.getStatus()) == null) {
            throw new ServiceException("用户状态错误", ResultCode.INVALID_PARA.getCode());
        }

        // 3. 更新用户基础信息。
        sysUser.setNickName(sysUserDTO.getNickName());
        sysUser.setStatus(sysUserDTO.getStatus());
        sysUser.setRemark(sysUserDTO.getRemark());

        // 4. 根据是否存在主键决定新增或更新。
        if (sysUser.getId() == null) {
            sysUserMapper.insert(sysUser);
        } else {
            sysUserMapper.updateById(sysUser);
        }

        // 5. 用户被禁用时删除其登录态。
        if (sysUserDTO.getUserId() != null && STATUS_DISABLE.equals(sysUserDTO.getStatus())) {
            tokenService.delLoginUser(sysUserDTO.getUserId(), USER_FROM_SYS);
        }
        return sysUser.getId();
    }

    /**
     * 查询 B 端用户列表。
     *
     * @param sysUserListReqDTO 用户查询请求
     * @return B 端用户列表
     */
    @Override
    public List<SysUserDTO> getUserList(SysUserListReqDTO sysUserListReqDTO) {
        // 1. 构造查询条件。
        SysUser searchSysUser = new SysUser();
        searchSysUser.setId(sysUserListReqDTO.getUserId());
        searchSysUser.setStatus(sysUserListReqDTO.getStatus());
        searchSysUser.setPhoneNumber(sysUserListReqDTO.getPhoneNumber());

        // 2. 执行查询并转换 DTO。
        List<SysUser> sysUserList = sysUserMapper.selectUserList(searchSysUser);
        return sysUserList.stream().map(this::toSysUserDTO).collect(Collectors.toList());
    }

    /**
     * 获取 B 端登录用户信息。
     *
     * @return B 端登录用户信息
     */
    @Override
    public SysUserLoginDTO getLoginUser() {
        // 1. 获取当前登录用户上下文。
        LoginUserDTO loginUserDTO = tokenService.getLoginUser();
        if (loginUserDTO == null || loginUserDTO.getUserId() == null) {
            throw new ServiceException("用户令牌有误", ResultCode.INVALID_PARA.getCode());
        }

        // 2. 查询数据库用户信息。
        SysUser sysUser = sysUserMapper.selectById(loginUserDTO.getUserId());
        if (sysUser == null) {
            throw new ServiceException("用户不存在", ResultCode.INVALID_PARA.getCode());
        }

        // 3. 合并登录上下文和用户实体信息。
        SysUserLoginDTO sysUserLoginDTO = new SysUserLoginDTO();
        BeanUtils.copyProperties(loginUserDTO, sysUserLoginDTO);
        BeanUtils.copyProperties(sysUser, sysUserLoginDTO);
        sysUserLoginDTO.setUserId(sysUser.getId());
        return sysUserLoginDTO;
    }

    /**
     * 转换用户 DTO。
     *
     * @param sysUser 用户实体
     * @return 用户 DTO
     */
    private SysUserDTO toSysUserDTO(SysUser sysUser) {
        SysUserDTO sysUserDTO = new SysUserDTO();
        sysUserDTO.setUserId(sysUser.getId());
        sysUserDTO.setPhoneNumber(sysUser.getPhoneNumber());
        sysUserDTO.setNickName(sysUser.getNickName());
        sysUserDTO.setIdentity(sysUser.getIdentity());
        sysUserDTO.setStatus(sysUser.getStatus());
        sysUserDTO.setRemark(sysUser.getRemark());
        return sysUserDTO;
    }

    /**
     * 校验手机号格式。
     *
     * @param phone 手机号
     * @return 格式正确返回 true
     */
    private boolean checkPhone(String phone) {
        return StringUtils.hasText(phone) && phone.matches("^1[3-9]\\d{9}$");
    }

    /**
     * 计算 SHA-256 十六进制摘要。
     *
     * @param text 原文
     * @return 十六进制摘要
     */
    private String sha256Hex(String text) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new ServiceException("密码加密失败", ResultCode.ERROR.getCode());
        }
    }
}
