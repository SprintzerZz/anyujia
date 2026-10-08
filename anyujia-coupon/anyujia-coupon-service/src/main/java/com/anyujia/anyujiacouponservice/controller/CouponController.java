package com.anyujia.anyujiacouponservice.controller;

import java.util.List;

import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.anyujia.anyujiacouponapi.domain.dto.CouponAvailableReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponLockReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponOrderActionReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponReceiveReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponTemplateCreateReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponTemplateListReqDTO;
import com.anyujia.anyujiacouponapi.domain.vo.CouponDiscountVO;
import com.anyujia.anyujiacouponapi.domain.vo.CouponGrantTaskVO;
import com.anyujia.anyujiacouponapi.domain.vo.CouponTemplateVO;
import com.anyujia.anyujiacouponapi.domain.vo.UserCouponVO;
import com.anyujia.anyujiacouponapi.feign.CouponFeignClient;
import com.anyujia.anyujiacouponservice.service.CouponGrantService;
import com.anyujia.anyujiacouponservice.service.CouponTemplateService;
import com.anyujia.anyujiacouponservice.service.UserCouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 平台服务费优惠券接口。
 */
@RestController
@RequiredArgsConstructor
public class CouponController implements CouponFeignClient {

    private final CouponTemplateService couponTemplateService;
    private final UserCouponService userCouponService;
    private final CouponGrantService couponGrantService;

    @Override
    public ApiResult<Long> createTemplate(String userFrom, @RequestBody @Valid CouponTemplateCreateReqDTO request) {
        requireSource(userFrom, "sys");
        return ApiResult.success(couponTemplateService.create(request));
    }

    @Override
    public ApiResult<BasePageVO<CouponTemplateVO>> listTemplates(String userFrom,
                                                                 @Valid CouponTemplateListReqDTO request) {
        requireSource(userFrom, "sys");
        return ApiResult.success(couponTemplateService.list(request));
    }

    @Override
    public ApiResult<Long> receive(Long userId, Integer userType, String userFrom,
                                   @RequestBody @Valid CouponReceiveReqDTO request) {
        requireSource(userFrom, "app");
        return ApiResult.success(userCouponService.receive(userId, userType, request));
    }

    @Override
    public ApiResult<String> receiveAsync(Long userId, Integer userType, String userFrom,
                                          @RequestBody @Valid CouponReceiveReqDTO request) {
        requireSource(userFrom, "app");
        return ApiResult.success(userCouponService.receiveAsync(userId, userType, request));
    }

    @Override
    public ApiResult<List<UserCouponVO>> listMine(Long userId, Integer userType, String userFrom, Integer status) {
        requireSource(userFrom, "app");
        return ApiResult.success(userCouponService.listMine(userId, userType, status));
    }

    @Override
    public ApiResult<List<UserCouponVO>> listAvailable(Long userId, Integer userType, String userFrom,
                                                       @RequestBody @Valid CouponAvailableReqDTO request) {
        requireSource(userFrom, "app");
        return ApiResult.success(userCouponService.listAvailable(userId, userType, request));
    }

    @Override
    public ApiResult<CouponDiscountVO> lock(Long userId, Integer userType, String userFrom,
                                            @RequestBody @Valid CouponLockReqDTO request) {
        requireSource(userFrom, "app");
        return ApiResult.success(userCouponService.lock(userId, userType, request));
    }

    @Override
    public ApiResult<Void> consume(String userFrom, @RequestBody @Valid CouponOrderActionReqDTO request) {
        requireOrderCaller(userFrom);
        userCouponService.consume(request.getOrderId());
        return ApiResult.success();
    }

    @Override
    public ApiResult<Void> release(String userFrom, @RequestBody @Valid CouponOrderActionReqDTO request) {
        requireOrderCaller(userFrom);
        userCouponService.release(request.getOrderId());
        return ApiResult.success();
    }

    @Override
    public ApiResult<Void> refund(String userFrom, @RequestBody @Valid CouponOrderActionReqDTO request) {
        requireOrderCaller(userFrom);
        userCouponService.refund(request.getOrderId());
        return ApiResult.success();
    }

    @PostMapping("/grant/task")
    public ApiResult<Long> createGrantTask(@RequestHeader("userFrom") String userFrom,
                                           @RequestParam("templateId") Long templateId,
                                           @RequestParam("userType") Integer userType,
                                           @RequestPart("file") MultipartFile file) {
        requireSource(userFrom, "sys");
        return ApiResult.success(couponGrantService.createTask(templateId, userType, file));
    }

    @GetMapping("/grant/task")
    public ApiResult<CouponGrantTaskVO> getGrantTask(@RequestHeader("userFrom") String userFrom,
                                                     @RequestParam("taskId") Long taskId) {
        requireSource(userFrom, "sys");
        return ApiResult.success(couponGrantService.getTask(taskId));
    }

    private void requireSource(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new ServiceException("当前账号无权执行该操作", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
    }

    private void requireOrderCaller(String userFrom) {
        if (!"service".equals(userFrom) && !"sys".equals(userFrom)) {
            throw new ServiceException("仅订单服务可以执行优惠券状态回调", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
    }
}
