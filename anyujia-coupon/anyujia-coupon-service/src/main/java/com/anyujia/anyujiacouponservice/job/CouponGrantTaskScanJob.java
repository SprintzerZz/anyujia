package com.anyujia.anyujiacouponservice.job;

import com.anyujia.anyujiacouponservice.service.CouponGrantService;
import com.anyujia.anyujiacouponservice.service.UserCouponExpireService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CouponGrantTaskScanJob {

    private final CouponGrantService couponGrantService;
    private final UserCouponExpireService userCouponExpireService;

    @Value("${coupon.expire.scan-limit:500}")
    private int expireScanLimit;

    @Scheduled(cron = "${coupon.grant.scan-cron:0 0/5 * * * ?}")
    @XxlJob("couponGrantTaskScanHandler")
    public void scan() {
        couponGrantService.scanStalledTasks();
    }

    @Scheduled(cron = "${coupon.expire.scan-cron:0 0/10 * * * ?}")
    @XxlJob("couponExpireScanHandler")
    public void scanExpiredCoupons() {
        int expired = userCouponExpireService.scanAndExpire(expireScanLimit);
        if (expired > 0) {
            log.info("Expired stale user coupons by scan, count={}", expired);
        }
    }
}
