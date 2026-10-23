package com.anyujia.anyujiacouponservice.service.impl;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.anyujia.anyujiacouponapi.constants.CouponConstants;
import com.anyujia.anyujiacouponservice.domain.entity.CouponTemplate;
import com.anyujia.anyujiacouponservice.mapper.CouponTemplateMapper;
import com.anyujia.anyujiacouponservice.mapper.UserCouponMapper;
import com.anyujia.anyujiacouponservice.mq.producer.CouponRedeemProducer;
import com.anyujia.anyujiacouponservice.service.CouponDiscountCalculator;
import com.anyujia.anyujiacouponservice.service.CouponReceiveCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponReceiveWarmLockTest {

    private CouponTemplateMapper templateMapper;
    private CouponReceiveCacheService receiveCacheService;
    private UserCouponServiceImpl service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(CouponTemplateMapper.class);
        UserCouponMapper userCouponMapper = mock(UserCouponMapper.class);
        receiveCacheService = mock(CouponReceiveCacheService.class);
        service = new UserCouponServiceImpl(
                templateMapper, userCouponMapper, new CouponDiscountCalculator(),
                receiveCacheService, mock(CouponRedeemProducer.class));
    }

    @Test
    void loadsTemplateOnlyOnceUnderConcurrentCacheMiss() throws Exception {
        AtomicBoolean warmed = new AtomicBoolean(false);
        when(receiveCacheService.reserve(10L, CouponConstants.USER_TYPE_TENANT, 1L))
                .thenAnswer(inv -> warmed.get()
                        ? CouponReceiveCacheService.ReserveResult.SUCCESS
                        : CouponReceiveCacheService.ReserveResult.CACHE_MISSING);
        doAnswer(inv -> {
            warmed.set(true);
            return null;
        }).when(receiveCacheService).warm(any(CouponTemplate.class));
        when(templateMapper.selectById(10L)).thenReturn(template());

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    service.receiveAsync(1L, CouponConstants.USER_TYPE_TENANT, 10L);
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(0, failures.get());
        verify(templateMapper, times(1)).selectById(10L);
    }

    private CouponTemplate template() {
        CouponTemplate template = new CouponTemplate();
        template.setId(10L);
        template.setStatus(CouponConstants.TEMPLATE_ENABLED);
        template.setApplicableUserType(CouponConstants.APPLICABLE_USER_ALL);
        template.setValidStartTime(LocalDateTime.now().minusDays(1));
        template.setValidEndTime(LocalDateTime.now().plusDays(1));
        return template;
    }
}
