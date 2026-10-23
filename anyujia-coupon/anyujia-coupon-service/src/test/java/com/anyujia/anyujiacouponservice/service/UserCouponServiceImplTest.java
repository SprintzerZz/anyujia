package com.anyujia.anyujiacouponservice.service;

import java.time.LocalDateTime;

import com.anyujia.anyujiacouponapi.constants.CouponConstants;
import com.anyujia.anyujiacouponservice.domain.entity.CouponTemplate;
import com.anyujia.anyujiacouponservice.domain.entity.UserCoupon;
import com.anyujia.anyujiacouponservice.mapper.CouponTemplateMapper;
import com.anyujia.anyujiacouponservice.mapper.UserCouponMapper;
import com.anyujia.anyujiacouponservice.mq.event.CouponRedeemEvent;
import com.anyujia.anyujiacouponservice.mq.producer.CouponRedeemProducer;
import com.anyujia.anyujiacouponservice.service.impl.UserCouponServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserCouponServiceImplTest {

    private CouponTemplateMapper templateMapper;
    private UserCouponMapper userCouponMapper;
    private CouponReceiveCacheService couponReceiveCacheService;
    private CouponRedeemProducer couponRedeemProducer;
    private UserCouponServiceImpl service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(CouponTemplateMapper.class);
        userCouponMapper = mock(UserCouponMapper.class);
        couponReceiveCacheService = mock(CouponReceiveCacheService.class);
        couponRedeemProducer = mock(CouponRedeemProducer.class);
        CouponDiscountCalculator calculator = new CouponDiscountCalculator();
        service = new UserCouponServiceImpl(
                templateMapper, userCouponMapper, calculator, couponReceiveCacheService, couponRedeemProducer);
    }

    @Test
    void receiveAsyncPublishesAfterReservation() {
        when(couponReceiveCacheService.reserve(10L, CouponConstants.USER_TYPE_TENANT, 1L))
                .thenReturn(CouponReceiveCacheService.ReserveResult.SUCCESS);

        String requestId = service.receiveAsync(1L, CouponConstants.USER_TYPE_TENANT, 10L);

        verify(couponRedeemProducer).send(org.mockito.ArgumentMatchers.<CouponRedeemEvent>argThat(event ->
                requestId.equals(event.requestId())
                        && event.userId().equals(1L)
                        && event.userType().equals(CouponConstants.USER_TYPE_TENANT)
                        && event.templateId().equals(10L)));
    }

    @Test
    void receiveAsyncRollsBackReservationWhenPublishFails() {
        when(couponReceiveCacheService.reserve(10L, CouponConstants.USER_TYPE_TENANT, 1L))
                .thenReturn(CouponReceiveCacheService.ReserveResult.SUCCESS);
        doThrow(new IllegalStateException("send failed"))
                .when(couponRedeemProducer).send(any(CouponRedeemEvent.class));

        assertThrows(IllegalStateException.class,
                () -> service.receiveAsync(1L, CouponConstants.USER_TYPE_TENANT, 10L));

        verify(couponReceiveCacheService).rollback(10L, CouponConstants.USER_TYPE_TENANT, 1L);
    }

    @Test
    void receiveAsyncFastFailsWhenRedisUnavailableWithoutDatabaseWarmup() {
        when(couponReceiveCacheService.reserve(10L, CouponConstants.USER_TYPE_TENANT, 1L))
                .thenReturn(CouponReceiveCacheService.ReserveResult.REDIS_UNAVAILABLE);

        assertThrows(RuntimeException.class,
                () -> service.receiveAsync(1L, CouponConstants.USER_TYPE_TENANT, 10L));

        verify(templateMapper, never()).selectById(10L);
        verify(couponRedeemProducer, never()).send(any(CouponRedeemEvent.class));
    }

    @Test
    void receiveFastFailsWhenRedisUnavailableWithoutDatabaseWrite() {
        when(couponReceiveCacheService.reserve(10L, CouponConstants.USER_TYPE_TENANT, 1L))
                .thenReturn(CouponReceiveCacheService.ReserveResult.REDIS_UNAVAILABLE);

        assertThrows(RuntimeException.class,
                () -> service.receive(1L, CouponConstants.USER_TYPE_TENANT, 10L));

        verify(templateMapper, never()).selectById(10L);
        verify(templateMapper, never()).decrementStock(10L, CouponConstants.USER_TYPE_TENANT);
        verify(userCouponMapper, never()).insert(any(UserCoupon.class));
    }

    @Test
    void receivePersistsActiveIdentity() {
        when(couponReceiveCacheService.reserve(10L, CouponConstants.USER_TYPE_LANDLORD, 1L))
                .thenReturn(CouponReceiveCacheService.ReserveResult.SUCCESS);
        when(userCouponMapper.selectCount(any())).thenReturn(0L);
        when(templateMapper.decrementStock(10L, CouponConstants.USER_TYPE_LANDLORD)).thenReturn(1);
        when(userCouponMapper.insert(any(UserCoupon.class))).thenAnswer(invocation -> {
            UserCoupon coupon = invocation.getArgument(0);
            coupon.setId(100L);
            return 1;
        });

        Long couponId = service.receive(1L, CouponConstants.USER_TYPE_LANDLORD, 10L);

        assertEquals(100L, couponId);
        verify(userCouponMapper).insert(org.mockito.ArgumentMatchers.<UserCoupon>argThat(coupon ->
                coupon.getUserType() == CouponConstants.USER_TYPE_LANDLORD));
    }

    @Test
    void receiveInvalidatesCacheWhenDatabaseRejectsStockDecrement() {
        when(couponReceiveCacheService.reserve(10L, CouponConstants.USER_TYPE_TENANT, 1L))
                .thenReturn(CouponReceiveCacheService.ReserveResult.SUCCESS);
        when(userCouponMapper.selectCount(any())).thenReturn(0L);
        when(templateMapper.decrementStock(10L, CouponConstants.USER_TYPE_TENANT)).thenReturn(0);

        assertThrows(RuntimeException.class,
                () -> service.receive(1L, CouponConstants.USER_TYPE_TENANT, 10L));

        verify(couponReceiveCacheService).invalidateReservation(
                10L, CouponConstants.USER_TYPE_TENANT, 1L);
    }

    @Test
    void receiveRollsBackReservationWhenUserCouponInsertFails() {
        when(couponReceiveCacheService.reserve(10L, CouponConstants.USER_TYPE_TENANT, 1L))
                .thenReturn(CouponReceiveCacheService.ReserveResult.SUCCESS);
        when(userCouponMapper.selectCount(any())).thenReturn(0L);
        when(templateMapper.decrementStock(10L, CouponConstants.USER_TYPE_TENANT)).thenReturn(1);
        when(userCouponMapper.insert(any(UserCoupon.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThrows(RuntimeException.class,
                () -> service.receive(1L, CouponConstants.USER_TYPE_TENANT, 10L));

        verify(couponReceiveCacheService).rollback(10L, CouponConstants.USER_TYPE_TENANT, 1L);
    }

    @Test
    void refundReturnsCouponWhenTemplateIsEffective() {
        UserCoupon coupon = usedCoupon();
        when(userCouponMapper.selectByOrderIdForUpdate(20L)).thenReturn(coupon);
        when(templateMapper.selectById(10L)).thenReturn(template(
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)));

        service.refund(20L);

        assertEquals(CouponConstants.USER_COUPON_UNUSED, coupon.getStatus());
        assertNull(coupon.getOrderId());
        assertNull(coupon.getUsedAt());
        verify(userCouponMapper).updateById(coupon);
    }

    @Test
    void refundExpiresCouponWhenTemplateHasExpired() {
        UserCoupon coupon = usedCoupon();
        when(userCouponMapper.selectByOrderIdForUpdate(20L)).thenReturn(coupon);
        when(templateMapper.selectById(10L)).thenReturn(template(
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1)));

        service.refund(20L);

        assertEquals(CouponConstants.USER_COUPON_EXPIRED, coupon.getStatus());
        assertNull(coupon.getOrderId());
        verify(userCouponMapper).updateById(coupon);
    }

    private UserCoupon usedCoupon() {
        UserCoupon coupon = new UserCoupon();
        coupon.setId(30L);
        coupon.setUserId(1L);
        coupon.setUserType(CouponConstants.USER_TYPE_TENANT);
        coupon.setTemplateId(10L);
        coupon.setOrderId(20L);
        coupon.setStatus(CouponConstants.USER_COUPON_USED);
        coupon.setUsedAt(LocalDateTime.now().minusMinutes(1));
        return coupon;
    }

    private CouponTemplate template(LocalDateTime startTime, LocalDateTime endTime) {
        CouponTemplate template = new CouponTemplate();
        template.setId(10L);
        template.setStatus(CouponConstants.TEMPLATE_ENABLED);
        template.setApplicableUserType(CouponConstants.APPLICABLE_USER_ALL);
        template.setValidStartTime(startTime);
        template.setValidEndTime(endTime);
        return template;
    }
}
