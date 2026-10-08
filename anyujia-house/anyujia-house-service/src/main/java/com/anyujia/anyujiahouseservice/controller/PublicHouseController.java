package com.anyujia.anyujiahouseservice.controller;

import com.anyujia.anyujiahouseapi.domain.vo.HousePublishVO;
import com.anyujia.anyujiahouseservice.service.HousePublishQueryService;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/house/publications")
@RequiredArgsConstructor
public class PublicHouseController {
    private final HousePublishQueryService queryService;

    @GetMapping
    public ApiResult<List<HousePublishVO>> list(@RequestParam(value = "limit", required = false) Integer limit) {
        return ApiResult.success(queryService.listPublic(limit));
    }

    @GetMapping("/{publishId}")
    public ApiResult<HousePublishVO> detail(@PathVariable Long publishId) {
        return ApiResult.success(queryService.detailPublic(publishId));
    }
}
