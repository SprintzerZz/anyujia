package com.anyujia.anyujiaadminapi.map.domin.dto;

import lombok.Data;

/**
 * 地图地点搜索请求参数。
 */
@Data
public class PlaceSearchReqDTO {

    /**
     * 城市或区域 ID。
     */
    private String id;

    /**
     * 当前页码。
     */
    private Integer pageIndex = 1;

    /**
     * 每页数量。
     */
    private Integer pageSize = 10;

    /**
     * 搜索关键词。
     */
    private String keyword;
}
