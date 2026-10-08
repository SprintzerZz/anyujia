package com.anyujia.anyujiaadminservice.user.domain.dto;

import lombok.Data;

/**
 * B 端用户列表查询请求 DTO。
 */
@Data
public class SysUserListReqDTO {

    /**
     * 用户主键 ID。
     */
    private Long userId;

    /**
     * 手机号。
     */
    private String phoneNumber;

    /**
     * 用户状态字典键。
     */
    private String status;
}
