package com.anyujia.anyujiahouseservice.controller;

import com.anyujia.anyujiahouseapi.domain.dto.HousePublishAuditReqDTO;
import com.anyujia.anyujiahouseapi.domain.dto.HousePublishCreateReqDTO;
import com.anyujia.anyujiahouseapi.domain.vo.HousePublishVO;
import com.anyujia.anyujiahouseservice.service.LandlordHousePublishService;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/house/publish")
@RequiredArgsConstructor
public class LandlordHousePublishController {
    private final LandlordHousePublishService service;

    @PostMapping
    public ApiResult<HousePublishVO> create(@RequestHeader("userId") Long userId,
                                            @RequestHeader("userType") Integer userType,
                                            @RequestHeader("userFrom") String userFrom,
                                            @RequestBody @Valid HousePublishCreateReqDTO request) {
        return ApiResult.success(service.create(userId, userType, userFrom, request));
    }

    @GetMapping("/mine")
    public ApiResult<List<HousePublishVO>> listMine(@RequestHeader("userId") Long userId,
                                                    @RequestHeader("userType") Integer userType,
                                                    @RequestHeader("userFrom") String userFrom) {
        return ApiResult.success(service.listMine(userId, userType, userFrom));
    }

    @PostMapping("/{publishId}/audit")
    public ApiResult<HousePublishVO> audit(@RequestHeader("userId") Long userId,
                                           @RequestHeader("userFrom") String userFrom,
                                           @PathVariable Long publishId,
                                           @RequestBody @Valid HousePublishAuditReqDTO request) {
        return ApiResult.success(service.audit(userId, userFrom, publishId, request));
    }
}
