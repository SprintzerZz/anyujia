package com.anyujia.anyujiaadminapi.app.user.domain.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * C 端用户响应 VO。
 */
@Data
public class AppUserVO {

    /**
     * 用户主键 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
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

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}
