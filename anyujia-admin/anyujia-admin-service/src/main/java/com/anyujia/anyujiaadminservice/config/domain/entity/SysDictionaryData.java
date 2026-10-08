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
 * 字典数据实体。
 *
 * <p>对应数据库表 {@code sys_dictionary_data}，用于保存某个字典类型下的具体字典项。</p>
 */
@Getter
@Setter
@TableName("sys_dictionary_data")
public class SysDictionaryData implements Serializable {

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
     * 字典类型键，对应 {@code sys_dictionary_type.type_key}。
     */
    @TableField("type_key")
    private String typeKey;

    /**
     * 字典数据业务键。
     */
    @TableField("data_key")
    private String dataKey;

    /**
     * 字典展示值。
     */
    @TableField("data_value")
    private String value;

    /**
     * 排序值，数值越小越靠前。
     */
    @TableField("sort")
    private Integer sort;

    /**
     * 状态：1 启用，0 停用。
     */
    @TableField("status")
    private Integer status;

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
