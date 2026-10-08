package com.anyujia.anyujiacouponapi.domain.dto;

import lombok.Data;

/**
 * 优惠券模板分页查询请求。
 */
@Data
public class CouponTemplateListReqDTO {

    private Integer pageIndex = 1;
    private Integer pageSize = 10;
    private String name;
    private Integer type;
    private Integer status;
    private Integer applicableUserType;
}
