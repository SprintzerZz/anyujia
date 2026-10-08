package com.anyujia.anyujiacouponservice.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.anyujia.anyujiacouponapi.constants.CouponConstants;
import com.anyujia.anyujiacouponservice.domain.entity.CouponTemplate;
import com.anyujia.anyujiacouponservice.domain.entity.UserCoupon;
import com.anyujia.anyujiacouponservice.exception.CouponStockConflictException;
import com.anyujia.anyujiacouponservice.mapper.CouponTemplateMapper;
import com.anyujia.anyujiacouponservice.mapper.UserCouponMapper;
import com.anyujia.anyujiacouponservice.mq.event.CouponRedeemEvent;
import com.anyujia.anyujiacouponservice.mq.producer.CouponExpireProducer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
public class CouponRedeemPersistenceService {

    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponMapper userCouponMapper;
    private final UserCouponIndexCacheService userCouponIndexCacheService;
    private final UserCouponCacheService userCouponCacheService;
    private final CouponTemplateCacheService couponTemplateCacheService;
    private final CouponExpireProducer couponExpireProducer;

    @Autowired
    public CouponRedeemPersistenceService(CouponTemplateMapper couponTemplateMapper,
                                          UserCouponMapper userCouponMapper,
                                          UserCouponIndexCacheService userCouponIndexCacheService,
                                          UserCouponCacheService userCouponCacheService,
                                          CouponTemplateCacheService couponTemplateCacheService,
                                          CouponExpireProducer couponExpireProducer) {
        this.couponTemplateMapper = couponTemplateMapper;
        this.userCouponMapper = userCouponMapper;
        this.userCouponIndexCacheService = userCouponIndexCacheService;
        this.userCouponCacheService = userCouponCacheService;
        this.couponTemplateCacheService = couponTemplateCacheService;
        this.couponExpireProducer = couponExpireProducer;
    }

    public CouponRedeemPersistenceService(CouponTemplateMapper couponTemplateMapper,
                                          UserCouponMapper userCouponMapper) {
        this.couponTemplateMapper = couponTemplateMapper;
        this.userCouponMapper = userCouponMapper;
        this.userCouponIndexCacheService = null;
        this.userCouponCacheService = null;
        this.couponTemplateCacheService = null;
        this.couponExpireProducer = null;
    }

    @Transactional(rollbackFor = Exception.class)
    public PersistResult persist(CouponRedeemEvent event) {
        LocalDateTime receivedAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.receivedAtEpochMillis()), ZoneId.systemDefault());
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(event.userId());
        userCoupon.setUserType(event.userType());
        userCoupon.setTemplateId(event.templateId());
        userCoupon.setReceiveRequestId(event.requestId());
        userCoupon.setStatus(CouponConstants.USER_COUPON_UNUSED);
        userCoupon.setReceivedAt(receivedAt);
        userCoupon.setUpdatedAt(receivedAt);
        try {
            userCouponMapper.insert(userCoupon);
        } catch (DuplicateKeyException ex) {
            Long sameRequestCount = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                    .eq(UserCoupon::getReceiveRequestId, event.requestId()));
            return sameRequestCount > 0
                    ? PersistResult.ALREADY_PERSISTED
                    : PersistResult.USER_ALREADY_RECEIVED;
        }

        if (couponTemplateMapper.decrementStock(event.templateId(), event.userType()) != 1) {
            throw new CouponStockConflictException("Database rejected coupon stock decrement");
        }
        CouponTemplate template = couponTemplateMapper.selectById(event.templateId());
        afterCommit(userCoupon, template);
        return PersistResult.CREATED;
    }

    private void afterCommit(UserCoupon userCoupon, CouponTemplate template) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            addIndexAndExpireMessage(userCoupon, template);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                addIndexAndExpireMessage(userCoupon, template);
            }
        });
    }

    private void addIndexAndExpireMessage(UserCoupon userCoupon, CouponTemplate template) {
        if (userCouponIndexCacheService != null) {
            userCouponIndexCacheService.addUnused(userCoupon, template);
        }
        if (userCouponCacheService != null) {
            userCouponCacheService.put(userCoupon);
        }
        if (couponTemplateCacheService != null) {
            couponTemplateCacheService.put(template);
        }
        if (couponExpireProducer != null) {
            try {
                couponExpireProducer.sendExpireMessage(userCoupon, template);
            } catch (RuntimeException ex) {
                log.warn("Send coupon expire message failed, userCouponId={}", userCoupon.getId(), ex);
            }
        }
    }

    public enum PersistResult {
        CREATED,
        ALREADY_PERSISTED,
        USER_ALREADY_RECEIVED
    }
}
