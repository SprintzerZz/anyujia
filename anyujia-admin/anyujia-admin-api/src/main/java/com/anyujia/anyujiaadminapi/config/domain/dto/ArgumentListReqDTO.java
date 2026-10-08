package com.anyujia.anyujiaadminapi.config.domain.dto;

import lombok.Data;

/**
 * 系统参数分页查询请求 DTO。
 */
@Data
public class ArgumentListReqDTO {

    /**
     * 当前页码。
     */
    private Integer pageIndex = 1;

    /**
     * 每页数量。
     */
    private Integer pageSize = 10;

    /**
     * 参数键名。
     */
    private String configKey;

    /**
     * 参数名称。
     */
    private String name;

    /**
     * 状态：1 启用，0 停用。
     */
    private Integer status;
}
