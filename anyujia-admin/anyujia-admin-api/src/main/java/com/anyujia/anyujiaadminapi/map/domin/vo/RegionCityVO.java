package com.anyujia.anyujiaadminapi.map.domin.vo;

import lombok.Data;

/**
 * 经纬度定位城市响应 VO。
 */
@Data
public class RegionCityVO {

    /**
     * 城市区划编码。
     */
    private String code;

    /**
     * 城市名称。
     */
    private String name;

    /**
     * 城市全称。
     */
    private String fullName;

    /**
     * 详细地址。
     */
    private String address;
}
