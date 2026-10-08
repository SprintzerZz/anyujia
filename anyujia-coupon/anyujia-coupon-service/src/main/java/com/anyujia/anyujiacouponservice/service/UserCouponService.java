package com.anyujia.anyujiacouponservice.service;

import java.util.List;

import com.anyujia.anyujiacouponapi.domain.dto.CouponAvailableReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponLockReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponReceiveReqDTO;
import com.anyujia.anyujiacouponapi.domain.vo.CouponDiscountVO;
import com.anyujia.anyujiacouponapi.domain.vo.UserCouponVO;

/**
 * 用户优惠券服务。
 */
public interface UserCouponService {

    Long receive(Long userId, Integer userType, CouponReceiveReqDTO request);

    String receiveAsync(Long userId, Integer userType, CouponReceiveReqDTO request);

    List<UserCouponVO> listMine(Long userId, Integer userType, Integer status);

    List<UserCouponVO> listAvailable(Long userId, Integer userType, CouponAvailableReqDTO request);

    CouponDiscountVO lock(Long userId, Integer userType, CouponLockReqDTO request);

    void consume(Long orderId);

    void release(Long orderId);

    void refund(Long orderId);
}
