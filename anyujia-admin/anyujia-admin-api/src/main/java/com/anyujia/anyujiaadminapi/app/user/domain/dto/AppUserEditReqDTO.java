package com.anyujia.anyujiaadminapi.app.user.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * C 端用户编辑请求 DTO。
 */
@Data
public class AppUserEditReqDTO {

    /**
     * 用户主键 ID。
     */
    @NotNull(message = "用户ID不能为空")
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
     * 用户头像地址。
     */
    private String avatarUrl;

    /**
     * 性别：0 未知，1 男，2 女。
     */
    private Integer gender;

    /**
     * 用户状态：1 启用，0 停用。
     */
    private Integer status;
}
