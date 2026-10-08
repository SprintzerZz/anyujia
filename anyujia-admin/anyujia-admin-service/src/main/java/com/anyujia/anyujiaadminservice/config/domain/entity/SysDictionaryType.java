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
 * 字典类型实体。
 *
 * <p>对应数据库表 {@code sys_dictionary_type}。</p>
 */
@Getter
@Setter
@TableName("sys_dictionary_type")
public class SysDictionaryType implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 字典类型键，例如 house_status。
     */
    @TableField("type_key")
    private String typeKey;

    /**
     * 字典类型名称，例如房源状态。
     */
    @TableField("type_name")
    private String typeName;

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
