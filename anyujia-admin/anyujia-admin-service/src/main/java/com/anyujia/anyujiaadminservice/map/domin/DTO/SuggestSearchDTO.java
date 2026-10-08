package com.anyujia.anyujiaadminservice.map.domin.DTO;

import lombok.Data;

/**
 * 腾讯位置服务关键词搜索请求 DTO。
 */
@Data
public class SuggestSearchDTO {

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
