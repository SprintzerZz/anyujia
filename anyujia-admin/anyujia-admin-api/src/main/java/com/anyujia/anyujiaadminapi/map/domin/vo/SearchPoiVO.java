package com.anyujia.anyujiaadminapi.map.domin.vo;

import lombok.Data;

/**
 * 地点搜索结果响应 VO。
 */
@Data
public class SearchPoiVO {

    /**
     * 地点名称。
     */
    private String title;

    /**
     * 地点地址。
     */
    private String address;

    /**
     * 经度。
     */
    private Double longitude;

    /**
     * 纬度。
     */
    private Double latitude;
}
