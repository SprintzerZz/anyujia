package com.anyujia.anyujiacouponapi.feign;

import java.util.List;

import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponAvailableReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponLockReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponOrderActionReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponReceiveReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponTemplateCreateReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponTemplateListReqDTO;
import com.anyujia.anyujiacouponapi.domain.vo.CouponDiscountVO;
import com.anyujia.anyujiacouponapi.domain.vo.CouponTemplateVO;
import com.anyujia.anyujiacouponapi.domain.vo.UserCouponVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 平台服务费优惠券远程调用接口。
 */
@FeignClient(contextId = "couponFeignClient", value = "anyujia-coupon", path = "/coupon")
public interface CouponFeignClient {

    @PostMapping("/template/create")
    ApiResult<Long> createTemplate(@RequestHeader("userFrom") String userFrom,
                                   @RequestBody CouponTemplateCreateReqDTO request);

    @GetMapping("/template/page")
    ApiResult<BasePageVO<CouponTemplateVO>> listTemplates(@RequestHeader("userFrom") String userFrom,
                                                          @SpringQueryMap CouponTemplateListReqDTO request);

    @PostMapping("/receive")
    ApiResult<Long> receive(@RequestHeader("userId") Long userId,
                            @RequestHeader("userType") Integer userType,
                            @RequestHeader("userFrom") String userFrom,
                            @RequestBody CouponReceiveReqDTO request);

    @PostMapping("/receive/async")
    ApiResult<String> receiveAsync(@RequestHeader("userId") Long userId,
                                   @RequestHeader("userType") Integer userType,
                                   @RequestHeader("userFrom") String userFrom,
                                   @RequestBody CouponReceiveReqDTO request);

    @GetMapping("/mine")
    ApiResult<List<UserCouponVO>> listMine(@RequestHeader("userId") Long userId,
                                           @RequestHeader("userType") Integer userType,
                                           @RequestHeader("userFrom") String userFrom,
                                           @RequestParam(value = "status", required = false) Integer status);

    @PostMapping("/available")
    ApiResult<List<UserCouponVO>> listAvailable(@RequestHeader("userId") Long userId,
                                                @RequestHeader("userType") Integer userType,
                                                @RequestHeader("userFrom") String userFrom,
                                                @RequestBody CouponAvailableReqDTO request);

    @PostMapping("/lock")
    ApiResult<CouponDiscountVO> lock(@RequestHeader("userId") Long userId,
                                     @RequestHeader("userType") Integer userType,
                                     @RequestHeader("userFrom") String userFrom,
                                     @RequestBody CouponLockReqDTO request);

    @PostMapping("/consume")
    ApiResult<Void> consume(@RequestHeader("userFrom") String userFrom,
                            @RequestBody CouponOrderActionReqDTO request);

    @PostMapping("/release")
    ApiResult<Void> release(@RequestHeader("userFrom") String userFrom,
                            @RequestBody CouponOrderActionReqDTO request);

    @PostMapping("/refund")
    ApiResult<Void> refund(@RequestHeader("userFrom") String userFrom,
                           @RequestBody CouponOrderActionReqDTO request);
}
