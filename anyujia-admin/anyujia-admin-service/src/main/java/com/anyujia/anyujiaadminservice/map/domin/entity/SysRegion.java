package com.anyujia.anyujiaadminservice.map.domin.entity;

import java.io.Serializable;
import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 行政区划实体。
 *
 * <p>对应数据库表 {@code sys_region}。</p>
 */
@Data
@TableName("sys_region")
public class SysRegion implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID，对应字段 {@code id}。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 区划编码，对应字段 {@code code}。
     */
    @TableField("code")
    private String code;

    /**
     * 父级 ID，对应字段 {@code parent_id}。
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 父级编码，对应字段 {@code parent_code}。
     */
    @TableField("parent_code")
    private String parentCode;

    /**
     * 区划名称，对应字段 {@code name}。
     */
    @TableField("name")
    private String name;

    /**
     * 区划全称，对应字段 {@code full_name}。
     */
    @TableField("full_name")
    private String fullName;

    /**
     * 城市拼音，对应字段 {@code pinyin}。
     */
    @TableField("pinyin")
    private String pinyin;

    /**
     * 区划层级，对应字段 {@code level}，取值含义：省-1，市-2，区-3。
     */
    @TableField("level")
    private Integer level;

    /**
     * 经度，对应字段 {@code longitude}。
     */
    @TableField("longitude")
    private BigDecimal longitude;

    /**
     * 纬度，对应字段 {@code latitude}。
     */
    @TableField("latitude")
    private BigDecimal latitude;
}
