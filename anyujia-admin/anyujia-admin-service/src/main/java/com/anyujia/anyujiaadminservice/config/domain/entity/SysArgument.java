package com.anyujia.anyujiaadminservice.config.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统参数实体。
 *
 * <p>对应数据库表 {@code sys_argument}。</p>
 */
@Getter
@Setter
@TableName("sys_argument")
public class SysArgument implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 自增主键。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 参数名称。
     */
    @TableField("name")
    private String name;

    /**
     * 参数键名。
     */
    @TableField("config_key")
    private String configKey;

    /**
     * 参数键值。
     */
    @TableField("value")
    private String value;

    /**
     * 备注。
     */
    @TableField("remark")
    private String remark;

    /**
     * 状态：1 启用，0 停用。
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
