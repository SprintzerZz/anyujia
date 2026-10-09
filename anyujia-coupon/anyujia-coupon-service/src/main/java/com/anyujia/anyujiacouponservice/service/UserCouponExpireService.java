package com.anyujia.anyujiacouponservice.service;

import java.util.List;

import com.anyujia.anyujiacouponapi.constants.CouponConstants;
import com.anyujia.anyujiacouponservice.domain.entity.UserCoupon;
import com.anyujia.anyujiacouponservice.mapper.CouponTemplateMapper;
import com.anyujia.anyujiacouponservice.mapper.UserCouponMapper;
import com.anyujia.anyujiacouponservice.mq.event.CouponExpireEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCouponExpireService {

    private final UserCouponMapper userCouponMapper;
    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponIndexCacheService userCouponIndexCacheService;
    private final UserCouponCacheService userCouponCacheService;

    @Transactional(rollbackFor = Exception.class)
    public boolean expire(CouponExpireEvent event) {
        int updated = userCouponMapper.expireUnused(
                event.userCouponId(),
                event.userId(),
                event.userType(),
                CouponConstants.USER_COUPON_UNUSED,
                CouponConstants.USER_COUPON_EXPIRED);
        if (updated == 1) {
            evict(event.userType(), event.userId(), event.userCouponId());
            return true;
        }
        return false;
    }

    @Transactional(rollbackFor = Exception.class)
    public int scanAndExpire(int limit) {
        List<Long> expiredTemplateIds = couponTemplateMapper.selectExpiredTemplateIds();
        if (expiredTemplateIds.isEmpty()) {
            return 0;
        }
        List<UserCoupon> coupons = userCouponMapper.selectOverdueUnused(
                CouponConstants.USER_COUPON_UNUSED, expiredTemplateIds, Math.max(1, limit));
        int expired = 0;
        for (UserCoupon coupon : coupons) {
            int updated = userCouponMapper.expireUnused(
                    coupon.getId(),
                    coupon.getUserId(),
                    coupon.getUserType(),
                    CouponConstants.USER_COUPON_UNUSED,
                    CouponConstants.USER_COUPON_EXPIRED);
            if (updated == 1) {
                expired++;
                evict(coupon.getUserType(), coupon.getUserId(), coupon.getId());
            }
        }
        return expired;
    }

    private void evict(Integer userType, Long userId, Long userCouponId) {
        userCouponIndexCacheService.remove(userType, userId, userCouponId);
        userCouponCacheService.delete(userCouponId);
    }
}
