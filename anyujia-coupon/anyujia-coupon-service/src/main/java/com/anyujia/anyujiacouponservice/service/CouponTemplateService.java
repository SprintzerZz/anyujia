package com.anyujia.anyujiacouponservice.service;

import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponTemplateCreateReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponTemplateListReqDTO;
import com.anyujia.anyujiacouponapi.domain.vo.CouponTemplateVO;

/**
 * 优惠券模板服务。
 */
public interface CouponTemplateService {

    Long create(CouponTemplateCreateReqDTO request);

    BasePageVO<CouponTemplateVO> list(CouponTemplateListReqDTO request);
}
