package com.anyujia.anyujiaadminapi.config.domain.dto;

import lombok.Data;

/**
 * 字典数据分页查询请求 DTO。
 */
@Data
public class DictionaryDataListReqDTO {

    /**
     * 当前页码。
     */
    private Integer pageIndex = 1;

    /**
     * 每页数量。
     */
    private Integer pageSize = 10;

    /**
     * 字典类型键。
     */
    private String typeKey;

    /**
     * 字典数据业务键。
     */
    private String dataKey;

    /**
     * 字典数据值。
     */
    private String value;

    /**
     * 状态：1 启用，0 停用。
     */
    private Integer status;
}
