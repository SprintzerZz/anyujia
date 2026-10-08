package com.anyujia.anyujiaadminservice.map.domin.DTO;

import lombok.Data;

/**
 * 地点搜索结果 DTO。
 */
@Data
public class SearchPoiDTO {

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
