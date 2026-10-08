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
 * B 端系统用户实体。
 *
 * <p>对应数据库表 {@code sys_user}。</p>
 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser implements Serializable {

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
     * 加密后的手机号。
     */
    @TableField("phone_number")
    private String phoneNumber;

    /**
     * SHA-256 加密后的密码。
     */
    @TableField("password")
    private String password;

    /**
     * 用户昵称。
     */
    @TableField("nick_name")
    private String nickName;

    /**
     * 用户身份字典键。
     */
    @TableField("identity")
    private String identity;

    /**
     * 用户状态字典键。
     */
    @TableField("status")
    private String status;

    /**
     * 备注。
     */
    @TableField("remark")
    private String remark;

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
