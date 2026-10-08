package com.anyujia.anyujiacouponservice.service;

import com.anyujia.anyujiacouponapi.constants.CouponConstants;
import com.anyujia.anyujiacouponservice.domain.entity.UserCoupon;
import com.anyujia.anyujiacouponservice.mapper.CouponTemplateMapper;
import com.anyujia.anyujiacouponservice.mapper.UserCouponMapper;
import com.anyujia.anyujiacouponservice.mq.event.CouponRedeemEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponRedeemPersistenceServiceTest {

    private CouponTemplateMapper templateMapper;
    private UserCouponMapper userCouponMapper;
    private CouponRedeemPersistenceService service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(CouponTemplateMapper.class);
        userCouponMapper = mock(UserCouponMapper.class);
        service = new CouponRedeemPersistenceService(templateMapper, userCouponMapper);
    }

    @Test
    void createsCouponAndDecrementsDatabaseStock() {
        when(userCouponMapper.insert(any(UserCoupon.class))).thenReturn(1);
        when(templateMapper.decrementStock(10L, CouponConstants.USER_TYPE_TENANT)).thenReturn(1);

        CouponRedeemPersistenceService.PersistResult result = service.persist(event("request-1"));

        assertEquals(CouponRedeemPersistenceService.PersistResult.CREATED, result);
        verify(templateMapper).decrementStock(10L, CouponConstants.USER_TYPE_TENANT);
    }

    @Test
    void recognizesDuplicateDeliveryByRequestId() {
        when(userCouponMapper.insert(any(UserCoupon.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(userCouponMapper.selectCount(any())).thenReturn(1L);

        CouponRedeemPersistenceService.PersistResult result = service.persist(event("request-1"));

        assertEquals(CouponRedeemPersistenceService.PersistResult.ALREADY_PERSISTED, result);
    }

    @Test
    void distinguishesAnotherRequestForExistingUserCoupon() {
        when(userCouponMapper.insert(any(UserCoupon.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(userCouponMapper.selectCount(any())).thenReturn(0L);

        CouponRedeemPersistenceService.PersistResult result = service.persist(event("request-2"));

        assertEquals(CouponRedeemPersistenceService.PersistResult.USER_ALREADY_RECEIVED, result);
    }

    private CouponRedeemEvent event(String requestId) {
        return new CouponRedeemEvent(
                requestId, 1L, CouponConstants.USER_TYPE_TENANT, 10L, System.currentTimeMillis());
    }
}
