package com.anyujia.anyujiaadminservice.user.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * C 端用户实体。
 *
 * <p>对应数据库表 {@code app_user}。</p>
 */
@Getter
@Setter
@TableName("app_user")
public class AppUser implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 用户主键 ID。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 微信 openId。
     */
    @TableField("open_id")
    private String openId;

    /**
     * 手机号。
     */
    @TableField("phone_number")
    private String phoneNumber;

    /**
     * 用户昵称。
     */
    @TableField("nickname")
    private String nickname;

    /**
     * 用户头像地址。
     */
    @TableField("avatar_url")
    private String avatarUrl;

    /**
     * 性别：0 未知，1 男，2 女。
     */
    @TableField("gender")
    private Integer gender;

    /**
     * 用户状态：1 启用，0 停用。
     */
    @TableField("status")
    private Integer status;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
