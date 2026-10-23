package com.anyujia.anyujiacouponservice.service;

import java.util.List;

import com.anyujia.anyujiacouponapi.constants.CouponConstants;
import com.anyujia.anyujiacouponservice.domain.entity.CouponGrantFailRecord;
import com.anyujia.anyujiacouponservice.domain.entity.UserCoupon;
import com.anyujia.anyujiacouponservice.mapper.CouponGrantFailRecordMapper;
import com.anyujia.anyujiacouponservice.mapper.CouponGrantTaskMapper;
import com.anyujia.anyujiacouponservice.mapper.CouponTemplateMapper;
import com.anyujia.anyujiacouponservice.mapper.UserCouponMapper;
import com.anyujia.anyujiacouponservice.mq.event.CouponGrantBatchEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponGrantBatchPersistenceServiceTest {

    private CouponTemplateMapper templateMapper;
    private UserCouponMapper userCouponMapper;
    private CouponGrantTaskMapper taskMapper;
    private CouponGrantFailRecordMapper failRecordMapper;
    private CouponGrantBatchPersistenceService service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(CouponTemplateMapper.class);
        userCouponMapper = mock(UserCouponMapper.class);
        taskMapper = mock(CouponGrantTaskMapper.class);
        failRecordMapper = mock(CouponGrantFailRecordMapper.class);
        service = new CouponGrantBatchPersistenceService(
                templateMapper, userCouponMapper, taskMapper, failRecordMapper);
    }

    @Test
    void recordsFailuresWithExactDuplicateUserIds() {
        when(userCouponMapper.selectExistingUserIds(
                eq(10L), eq(CouponConstants.USER_TYPE_TENANT), anyList()))
                .thenReturn(List.of(2L, 4L));
        when(userCouponMapper.insertIgnoreBatch(anyList())).thenReturn(2);
        when(templateMapper.decrementStockBatch(10L, CouponConstants.USER_TYPE_TENANT, 2)).thenReturn(1);

        service.persist(event(List.of(1L, 2L, 3L, 4L)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserCoupon>> insertCaptor = ArgumentCaptor.forClass(List.class);
        verify(userCouponMapper).insertIgnoreBatch(insertCaptor.capture());
        List<Long> insertedUserIds = insertCaptor.getValue().stream()
                .map(UserCoupon::getUserId).toList();
        assertEquals(List.of(1L, 3L), insertedUserIds);

        ArgumentCaptor<CouponGrantFailRecord> failCaptor =
                ArgumentCaptor.forClass(CouponGrantFailRecord.class);
        verify(failRecordMapper, times(2)).insert(failCaptor.capture());
        List<Long> failedUserIds = failCaptor.getAllValues().stream()
                .map(CouponGrantFailRecord::getUserId).toList();
        assertEquals(List.of(2L, 4L), failedUserIds);
    }

    @Test
    void doesNotRecordFailuresWhenAllUsersAreNew() {
        when(userCouponMapper.selectExistingUserIds(
                eq(10L), eq(CouponConstants.USER_TYPE_TENANT), anyList()))
                .thenReturn(List.of());
        when(userCouponMapper.insertIgnoreBatch(anyList())).thenReturn(3);
        when(templateMapper.decrementStockBatch(10L, CouponConstants.USER_TYPE_TENANT, 3)).thenReturn(1);

        service.persist(event(List.of(1L, 2L, 3L)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserCoupon>> insertCaptor = ArgumentCaptor.forClass(List.class);
        verify(userCouponMapper).insertIgnoreBatch(insertCaptor.capture());
        List<Long> insertedUserIds = insertCaptor.getValue().stream()
                .map(UserCoupon::getUserId).toList();
        assertEquals(List.of(1L, 2L, 3L), insertedUserIds);

        verify(failRecordMapper, never()).insert(any(CouponGrantFailRecord.class));
    }

    private CouponGrantBatchEvent event(List<Long> userIds) {
        return new CouponGrantBatchEvent(
                "batch-1", 1L, 10L, CouponConstants.USER_TYPE_TENANT,
                List.copyOf(userIds), System.currentTimeMillis());
    }
}
