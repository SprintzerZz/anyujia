package com.anyujia.anyujiahouseservice.controller;

import com.anyujia.anyujiahouseapi.domain.vo.HousePublishConsultVO;
import com.anyujia.anyujiahouseapi.domain.vo.HousePublishVO;
import com.anyujia.anyujiahouseapi.feign.HousePublishFeignClient;
import com.anyujia.anyujiahouseservice.service.HousePublishQueryService;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/internal/house-publish")
public class HousePublishController implements HousePublishFeignClient {
    private final HousePublishQueryService queryService;

    public HousePublishController(HousePublishQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    @GetMapping("/{housePublishId}/consultable")
    public ApiResult<HousePublishConsultVO> getConsultable(@PathVariable("housePublishId") Long housePublishId) {
        return ApiResult.success(queryService.getConsultable(housePublishId));
    }

    @Override
    @GetMapping("/{housePublishId}/snapshot")
    public ApiResult<HousePublishVO> getSnapshot(@PathVariable("housePublishId") Long housePublishId) {
        return ApiResult.success(queryService.detailPublic(housePublishId));
    }
}
