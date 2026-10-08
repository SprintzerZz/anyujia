package com.anyujia.anyujiacommondomain.domain.vo;

import java.util.Collections;
import java.util.List;

import lombok.Data;

/**
 * 分页响应基础对象。
 *
 * @param <T> 列表数据类型
 */
@Data
public class BasePageVO<T> {

    /**
     * 当前页码。
     */
    private Integer pageIndex = 1;

    /**
     * 每页数量。
     */
    private Integer pageSize = 10;

    /**
     * 总记录数。
     */
    private Long total = 0L;

    /**
     * 当前页数据列表。
     */
    private List<T> list = Collections.emptyList();
}
