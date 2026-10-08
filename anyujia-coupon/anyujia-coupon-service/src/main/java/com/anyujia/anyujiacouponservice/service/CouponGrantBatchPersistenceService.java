package com.anyujia.anyujiacouponservice.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import com.anyujia.anyujiacouponapi.constants.CouponConstants;
import com.anyujia.anyujiacouponservice.domain.entity.CouponGrantFailRecord;
import com.anyujia.anyujiacouponservice.domain.entity.CouponGrantTask;
import com.anyujia.anyujiacouponservice.domain.entity.CouponTemplate;
import com.anyujia.anyujiacouponservice.domain.entity.UserCoupon;
import com.anyujia.anyujiacouponservice.exception.CouponStockConflictException;
import com.anyujia.anyujiacouponservice.mapper.CouponGrantFailRecordMapper;
import com.anyujia.anyujiacouponservice.mapper.CouponGrantTaskMapper;
import com.anyujia.anyujiacouponservice.mapper.CouponTemplateMapper;
import com.anyujia.anyujiacouponservice.mapper.UserCouponMapper;
import com.anyujia.anyujiacouponservice.mq.event.CouponGrantBatchEvent;
import com.anyujia.anyujiacouponservice.mq.producer.CouponExpireProducer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponGrantBatchPersistenceService {

    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponMapper userCouponMapper;
    private final CouponGrantTaskMapper couponGrantTaskMapper;
    private final CouponGrantFailRecordMapper failRecordMapper;
    private final UserCouponIndexCacheService userCouponIndexCacheService;
    private final UserCouponCacheService userCouponCacheService;
    private final CouponTemplateCacheService couponTemplateCacheService;
    private final CouponExpireProducer couponExpireProducer;

    @Transactional(rollbackFor = Exception.class)
    public void persist(CouponGrantBatchEvent event) {
        if (event.userIds() == null || event.userIds().isEmpty()) {
            return;
        }
        LocalDateTime receivedAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.receivedAtEpochMillis()), ZoneId.systemDefault());
        List<UserCoupon> coupons = event.userIds().stream()
                .distinct()
                .map(userId -> {
                    UserCoupon coupon = new UserCoupon();
                    coupon.setUserId(userId);
                    coupon.setUserType(event.userType());
                    coupon.setTemplateId(event.templateId());
                    coupon.setReceiveRequestId(event.batchId() + "-" + userId);
                    coupon.setStatus(CouponConstants.USER_COUPON_UNUSED);
                    coupon.setReceivedAt(receivedAt);
                    coupon.setUpdatedAt(receivedAt);
                    return coupon;
                })
                .toList();

        int inserted = userCouponMapper.insertIgnoreBatch(coupons);
        if (inserted > 0 && couponTemplateMapper.decrementStockBatch(
                event.templateId(), event.userType(), inserted) != 1) {
            throw new CouponStockConflictException("Database rejected coupon grant stock decrement");
        }

        int failed = coupons.size() - inserted;
        if (failed > 0) {
            recordFailures(event, coupons.stream().limit(failed).toList(), "重复领取或已存在用户券");
        }
        couponGrantTaskMapper.increaseProgress(event.taskId(), inserted, failed);
        finishTaskIfDone(event.taskId());
        CouponTemplate template = couponTemplateMapper.selectById(event.templateId());
        addCacheAfterCommit(persistedCoupons(event), template);
    }

    private List<UserCoupon> persistedCoupons(CouponGrantBatchEvent event) {
        return userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getTemplateId, event.templateId())
                .eq(UserCoupon::getUserType, event.userType())
                .in(UserCoupon::getUserId, event.userIds())
                .eq(UserCoupon::getStatus, CouponConstants.USER_COUPON_UNUSED));
    }

    private void recordFailures(CouponGrantBatchEvent event, List<UserCoupon> coupons, String reason) {
        LocalDateTime now = LocalDateTime.now();
        coupons.forEach(coupon -> {
            CouponGrantFailRecord record = new CouponGrantFailRecord();
            record.setTaskId(event.taskId());
            record.setTemplateId(event.templateId());
            record.setUserId(coupon.getUserId());
            record.setUserType(event.userType());
            record.setReason(reason);
            record.setCreatedAt(now);
            failRecordMapper.insert(record);
        });
    }

    private void finishTaskIfDone(Long taskId) {
        CouponGrantTask task = couponGrantTaskMapper.selectById(taskId);
        if (task != null && task.getSuccessCount() + task.getFailureCount() >= task.getTotalCount()) {
            couponGrantTaskMapper.updateStatus(taskId, CouponConstants.GRANT_TASK_FINISHED, "批量发券完成");
        }
    }

    private void addCacheAfterCommit(List<UserCoupon> coupons, CouponTemplate template) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            addCache(coupons, template);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                addCache(coupons, template);
            }
        });
    }

    private void addCache(List<UserCoupon> coupons, CouponTemplate template) {
        userCouponIndexCacheService.addUnusedBatch(coupons, template);
        userCouponCacheService.putBatch(coupons);
        couponTemplateCacheService.put(template);
        coupons.forEach(coupon -> {
            try {
                couponExpireProducer.sendExpireMessage(coupon, template);
            } catch (RuntimeException ex) {
                log.warn("Send coupon expire message failed, userCouponId={}", coupon.getId(), ex);
            }
        });
    }
}
