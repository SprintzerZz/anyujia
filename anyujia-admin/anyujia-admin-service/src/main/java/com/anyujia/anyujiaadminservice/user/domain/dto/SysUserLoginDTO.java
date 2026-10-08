package com.anyujia.anyujiaadminservice.user.domain.dto;

import lombok.Data;

/**
 * B 端当前登录用户信息 DTO。
 */
@Data
public class SysUserLoginDTO {

    /**
     * 用户主键 ID。
     */
    private Long userId;

    /**
     * 登录用户名。
     */
    private String userName;

    /**
     * 用户来源。
     */
    private String userFrom;

    /**
     * 用户昵称。
     */
    private String nickName;

    /**
     * 手机号。
     */
    private String phoneNumber;

    /**
     * 用户身份字典键。
     */
    private String identity;

    /**
     * 用户状态字典键。
     */
    private String status;

    /**
     * 备注。
     */
    private String remark;
}
