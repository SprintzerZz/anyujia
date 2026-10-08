package com.anyujia.anyujiaadminapi.map.domin.vo;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 行政区划响应 VO。
 *
 * <p>字段来源于 {@code sys_region} 表，同时匹配地图城市列表接口返回结构。</p>
 */
@Data
public class RegionVO {

    /**
     * 主键 ID，对应 sys_region.id。
     */
    private Long id;

    /**
     * 区划编码，对应 sys_region.code。
     */
    private String code;

    /**
     * 父级 ID，对应 sys_region.parent_id。
     */
    private Long parentId;

    /**
     * 父级区划编码，对应 sys_region.parent_code。
     */
    private String parentCode;

    /**
     * 区划名称，对应 sys_region.name。
     */
    private String name;

    /**
     * 区划全称，对应 sys_region.full_name。
     */
    private String fullName;

    /**
     * 城市拼音，对应 sys_region.pinyin。
     */
    private String pinyin;

    /**
     * 区划层级：省-1，市-2，区-3，对应 sys_region.level。
     */
    private Integer level;

    /**
     * 经度，对应 sys_region.longitude。
     */
    private BigDecimal longitude;

    /**
     * 纬度，对应 sys_region.latitude。
     */
    private BigDecimal latitude;
}
