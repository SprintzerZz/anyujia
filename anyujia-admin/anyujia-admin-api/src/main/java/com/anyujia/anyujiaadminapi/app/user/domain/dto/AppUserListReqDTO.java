package com.anyujia.anyujiaadminapi.app.user.domain.dto;

import lombok.Data;

/**
 * C 端用户分页查询请求 DTO。
 */
@Data
public class AppUserListReqDTO {

    /**
     * 当前页码。
     */
    private Integer pageIndex = 1;

    /**
     * 每页数量。
     */
    private Integer pageSize = 10;

    /**
     * 用户主键 ID。
     */
    private Long id;

    /**
     * 微信 openId。
     */
    private String openId;

    /**
     * 手机号。
     */
    private String phoneNumber;

    /**
     * 用户昵称。
     */
    private String nickname;

    /**
     * 用户状态：1 启用，0 停用。
     */
    private Integer status;
}
