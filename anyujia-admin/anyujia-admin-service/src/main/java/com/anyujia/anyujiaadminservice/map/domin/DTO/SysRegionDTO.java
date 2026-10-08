package com.anyujia.anyujiaadminservice.map.domin.DTO;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 行政区划 DTO。
 */
@Data
public class SysRegionDTO implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID。
     */
    private Long id;

    /**
     * 区划编码。
     */
    private String code;

    /**
     * 父级 ID。
     */
    private Long parentId;

    /**
     * 父级编码。
     */
    private String parentCode;

    /**
     * 区划名称。
     */
    private String name;

    /**
     * 区划全称。
     */
    private String fullName;

    /**
     * 城市拼音。
     */
    private String pinyin;

    /**
     * 区划层级：省-1，市-2，区-3。
     */
    private Integer level;

    /**
     * 经度。
     */
    private BigDecimal longitude;

    /**
     * 纬度。
     */
    private BigDecimal latitude;
}
