package com.anyujia.anyujiacouponservice.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.anyujia.anyujiacouponapi.constants.CouponConstants;
import com.anyujia.anyujiacouponapi.domain.dto.CouponAvailableReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponLockReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponReceiveReqDTO;
import com.anyujia.anyujiacouponapi.domain.vo.CouponDiscountVO;
import com.anyujia.anyujiacouponapi.domain.vo.UserCouponVO;
import com.anyujia.anyujiacouponservice.domain.entity.CouponTemplate;
import com.anyujia.anyujiacouponservice.domain.entity.UserCoupon;
import com.anyujia.anyujiacouponservice.mapper.CouponTemplateMapper;
import com.anyujia.anyujiacouponservice.mapper.UserCouponMapper;
import com.anyujia.anyujiacouponservice.mq.event.CouponRedeemEvent;
import com.anyujia.anyujiacouponservice.mq.producer.CouponExpireProducer;
import com.anyujia.anyujiacouponservice.mq.producer.CouponRedeemProducer;
import com.anyujia.anyujiacouponservice.service.CouponDiscountCalculator;
import com.anyujia.anyujiacouponservice.service.CouponReceiveCacheService;
import com.anyujia.anyujiacouponservice.service.CouponTemplateCacheService;
import com.anyujia.anyujiacouponservice.service.UserCouponCacheService;
import com.anyujia.anyujiacouponservice.service.UserCouponIndexCacheService;
import com.anyujia.anyujiacouponservice.service.UserCouponService;
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
public class UserCouponServiceImpl implements UserCouponService {

    private static final int AVAILABLE_COUPON_LIMIT = 100;

    private final ConcurrentHashMap<Long, Object> warmLocks = new ConcurrentHashMap<>();

    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponMapper userCouponMapper;
    private final CouponDiscountCalculator discountCalculator;
    private final CouponReceiveCacheService couponReceiveCacheService;
    private final CouponRedeemProducer couponRedeemProducer;
    private final UserCouponIndexCacheService userCouponIndexCacheService;
    private final UserCouponCacheService userCouponCacheService;
    private final CouponTemplateCacheService couponTemplateCacheService;
    private final CouponExpireProducer couponExpireProducer;

    @Autowired
    public UserCouponServiceImpl(CouponTemplateMapper couponTemplateMapper,
                                 UserCouponMapper userCouponMapper,
                                 CouponDiscountCalculator discountCalculator,
                                 CouponReceiveCacheService couponReceiveCacheService,
                                 CouponRedeemProducer couponRedeemProducer,
                                 UserCouponIndexCacheService userCouponIndexCacheService,
                                 UserCouponCacheService userCouponCacheService,
                                 CouponTemplateCacheService couponTemplateCacheService,
                                 CouponExpireProducer couponExpireProducer) {
        this.couponTemplateMapper = couponTemplateMapper;
        this.userCouponMapper = userCouponMapper;
        this.discountCalculator = discountCalculator;
        this.couponReceiveCacheService = couponReceiveCacheService;
        this.couponRedeemProducer = couponRedeemProducer;
        this.userCouponIndexCacheService = userCouponIndexCacheService;
        this.userCouponCacheService = userCouponCacheService;
        this.couponTemplateCacheService = couponTemplateCacheService;
        this.couponExpireProducer = couponExpireProducer;
    }

    public UserCouponServiceImpl(CouponTemplateMapper couponTemplateMapper,
                                 UserCouponMapper userCouponMapper,
                                 CouponDiscountCalculator discountCalculator,
                                 CouponReceiveCacheService couponReceiveCacheService,
                                 CouponRedeemProducer couponRedeemProducer) {
        this.couponTemplateMapper = couponTemplateMapper;
        this.userCouponMapper = userCouponMapper;
        this.discountCalculator = discountCalculator;
        this.couponReceiveCacheService = couponReceiveCacheService;
        this.couponRedeemProducer = couponRedeemProducer;
        this.userCouponIndexCacheService = null;
        this.userCouponCacheService = null;
        this.couponTemplateCacheService = null;
        this.couponExpireProducer = null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long receive(Long userId, Integer userType, CouponReceiveReqDTO request) {
        requireUser(userId, userType);
        reserveCoupon(request, userType, userId);
        AtomicBoolean reservationResolved = new AtomicBoolean(false);
        rollbackAfterTransactionFailure(request.getTemplateId(), userType, userId, reservationResolved);
        try {
            if (couponTemplateMapper.decrementStock(request.getTemplateId(), userType) != 1) {
                reservationResolved.set(true);
                couponReceiveCacheService.invalidateReservation(request.getTemplateId(), userType, userId);
                throw invalid("优惠券不存在、未生效或已领完");
            }
            UserCoupon userCoupon = new UserCoupon();
            userCoupon.setUserId(userId);
            userCoupon.setUserType(userType);
            userCoupon.setTemplateId(request.getTemplateId());
            userCoupon.setReceiveRequestId(requestId(request.getRequestId()));
            userCoupon.setStatus(CouponConstants.USER_COUPON_UNUSED);
            userCoupon.setReceivedAt(LocalDateTime.now());
            userCoupon.setUpdatedAt(LocalDateTime.now());
            try {
                userCouponMapper.insert(userCoupon);
                CouponTemplate template = couponTemplateMapper.selectById(request.getTemplateId());
                addCacheAfterCommit(userCoupon, template);
            } catch (DuplicateKeyException ex) {
                rollbackReservation(request.getTemplateId(), userType, userId, reservationResolved);
                throw invalid("每位用户限领一张");
            }
            return userCoupon.getId();
        } catch (RuntimeException ex) {
            rollbackReservation(request.getTemplateId(), userType, userId, reservationResolved);
            throw ex;
        }
    }

    public Long receive(Long userId, Integer userType, Long templateId) {
        CouponReceiveReqDTO request = new CouponReceiveReqDTO();
        request.setTemplateId(templateId);
        return receive(userId, userType, request);
    }

    @Override
    public String receiveAsync(Long userId, Integer userType, CouponReceiveReqDTO request) {
        requireUser(userId, userType);
        reserveCoupon(request, userType, userId);
        String requestId = requestId(request.getRequestId());
        try {
            couponRedeemProducer.send(new CouponRedeemEvent(
                    requestId, userId, userType, request.getTemplateId(), System.currentTimeMillis()));
        } catch (RuntimeException ex) {
            couponReceiveCacheService.rollback(request.getTemplateId(), userType, userId);
            throw ex;
        }
        return requestId;
    }

    public String receiveAsync(Long userId, Integer userType, Long templateId) {
        CouponReceiveReqDTO request = new CouponReceiveReqDTO();
        request.setTemplateId(templateId);
        return receiveAsync(userId, userType, request);
    }

    private void reserveCoupon(CouponReceiveReqDTO request, Integer userType, Long userId) {
        CouponReceiveCacheService.ReserveResult result = reserveFromCache(request, userType, userId);
        if (result == CouponReceiveCacheService.ReserveResult.CACHE_MISSING) {
            result = warmThenReserve(request, userType, userId);
        }
        switch (result) {
            case SUCCESS -> {
            }
            case OUT_OF_STOCK -> throw invalid("优惠券已领完");
            case ALREADY_RECEIVED -> throw invalid("每位用户限领一张");
            case USER_TYPE_MISMATCH -> throw invalid("当前身份或区域不适用该优惠券");
            case CACHE_MISSING, TEMPLATE_UNAVAILABLE -> throw invalid("优惠券不存在或已失效");
            default -> throw new IllegalStateException("Unsupported coupon reserve result: " + result);
        }
    }

    private CouponReceiveCacheService.ReserveResult reserveFromCache(CouponReceiveReqDTO request,
                                                                     Integer userType,
                                                                     Long userId) {
        if ((request.getProvinceCode() == null || request.getProvinceCode().isBlank())
                && (request.getCityCode() == null || request.getCityCode().isBlank())
                && (request.getDistrictCode() == null || request.getDistrictCode().isBlank())) {
            return couponReceiveCacheService.reserve(request.getTemplateId(), userType, userId);
        }
        return couponReceiveCacheService.reserve(
                request.getTemplateId(), userType, userId,
                request.getProvinceCode(), request.getCityCode(), request.getDistrictCode());
    }

    private CouponReceiveCacheService.ReserveResult warmThenReserve(CouponReceiveReqDTO request,
                                                                    Integer userType,
                                                                    Long userId) {
        Long templateId = request.getTemplateId();
        Object lock = warmLocks.computeIfAbsent(templateId, id -> new Object());
        synchronized (lock) {
            CouponReceiveCacheService.ReserveResult result = reserveFromCache(request, userType, userId);
            if (result != CouponReceiveCacheService.ReserveResult.CACHE_MISSING) {
                return result;
            }
            CouponTemplate template = couponTemplateMapper.selectById(templateId);
            if (template == null) {
                throw invalid("优惠券不存在或已失效");
            }
            couponReceiveCacheService.warm(template);
            return reserveFromCache(request, userType, userId);
        }
    }

    private void rollbackAfterTransactionFailure(Long templateId, Integer userType, Long userId,
                                                 AtomicBoolean reservationResolved) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    rollbackReservation(templateId, userType, userId, reservationResolved);
                }
            }
        });
    }

    private void rollbackReservation(Long templateId, Integer userType, Long userId,
                                     AtomicBoolean reservationResolved) {
        if (reservationResolved.compareAndSet(false, true)) {
            couponReceiveCacheService.rollback(templateId, userType, userId);
        }
    }

    @Override
    public List<UserCouponVO> listMine(Long userId, Integer userType, Integer status) {
        requireUser(userId, userType);
        List<UserCoupon> coupons = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getUserType, userType)
                .eq(status != null, UserCoupon::getStatus, status)
                .orderByDesc(UserCoupon::getId));
        return toVOList(coupons);
    }

    @Override
    public List<UserCouponVO> listAvailable(Long userId, Integer userType, CouponAvailableReqDTO request) {
        requireUser(userId, userType);
        List<UserCouponVO> coupons = toVOList(cachedUnusedCoupons(userId, userType));
        LocalDateTime now = LocalDateTime.now();
        return coupons.stream()
                .filter(item -> !now.isBefore(item.getValidStartTime()) && now.isBefore(item.getValidEndTime()))
                .filter(item -> matchRegion(item, request.getProvinceCode(), request.getCityCode(), request.getDistrictCode()))
                .filter(item -> calculate(item, request.getServiceFeeAmount()).compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing((UserCouponVO item) -> calculate(item, request.getServiceFeeAmount())).reversed())
                .toList();
    }

    private List<UserCoupon> cachedUnusedCoupons(Long userId, Integer userType) {
        Set<String> cachedIds = userCouponIndexCacheService == null
                ? Set.of()
                : userCouponIndexCacheService.currentUnusedIds(userType, userId, AVAILABLE_COUPON_LIMIT);
        if (cachedIds != null && !cachedIds.isEmpty()) {
            List<Long> ids = cachedIds.stream().map(Long::valueOf).toList();
            Map<Long, UserCoupon> cachedMap = userCouponCacheService == null
                    ? Map.of()
                    : userCouponCacheService.getBatch(ids);
            List<Long> missedIds = ids.stream().filter(id -> !cachedMap.containsKey(id)).toList();
            Map<Long, UserCoupon> merged = new HashMap<>(cachedMap);
            if (!missedIds.isEmpty()) {
                List<UserCoupon> missed = userCouponMapper.selectBatchIds(missedIds);
                putUserCouponCacheBatch(missed);
                missed.forEach(item -> merged.put(item.getId(), item));
            }
            List<UserCoupon> cached = ids.stream()
                    .map(merged::get)
                    .filter(Objects::nonNull)
                    .filter(item -> item.getStatus() == CouponConstants.USER_COUPON_UNUSED)
                    .toList();
            if (!cached.isEmpty()) {
                return cached;
            }
        }
        return userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getUserType, userType)
                .eq(UserCoupon::getStatus, CouponConstants.USER_COUPON_UNUSED)
                .orderByDesc(UserCoupon::getId)
                .last("limit " + AVAILABLE_COUPON_LIMIT));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponDiscountVO lock(Long userId, Integer userType, CouponLockReqDTO request) {
        requireUser(userId, userType);
        UserCoupon userCoupon = userCouponMapper.selectById(request.getUserCouponId());
        if (userCoupon == null || !userId.equals(userCoupon.getUserId())
                || !userType.equals(userCoupon.getUserType())) {
            throw invalid("用户优惠券不存在");
        }
        if (userCoupon.getStatus() != CouponConstants.USER_COUPON_UNUSED) {
            throw invalid("优惠券当前状态不可锁定");
        }
        if (userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getOrderId, request.getOrderId())) > 0) {
            throw invalid("该服务费订单已经使用优惠券");
        }

        CouponTemplate template = requireEffectiveTemplate(userCoupon.getTemplateId(), userType);
        requireRegion(template, request.getProvinceCode(), request.getCityCode(), request.getDistrictCode());
        BigDecimal discount = discountCalculator.calculate(template, request.getServiceFeeAmount());
        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            throw invalid("平台服务费未达到优惠券使用条件");
        }

        if (userCouponMapper.lockByStatus(userCoupon.getId(), userId, userType, request.getOrderId(),
                request.getServiceFeeAmount(), discount, CouponConstants.USER_COUPON_UNUSED,
                CouponConstants.USER_COUPON_LOCKED) != 1) {
            throw invalid("优惠券当前状态不可锁定");
        }
        removeUnusedIndex(userType, userId, userCoupon.getId());
        deleteUserCouponCache(userCoupon.getId());

        CouponDiscountVO result = new CouponDiscountVO();
        result.setUserCouponId(userCoupon.getId());
        result.setOrderId(request.getOrderId());
        result.setOriginalAmount(request.getServiceFeeAmount());
        result.setDiscountAmount(discount);
        result.setPayableAmount(request.getServiceFeeAmount().subtract(discount));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consume(Long orderId) {
        if (userCouponMapper.updateOrderStatus(orderId, CouponConstants.USER_COUPON_LOCKED,
                CouponConstants.USER_COUPON_USED) != 1) {
            throw invalid("只有已锁定的优惠券可以核销");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long orderId) {
        UserCoupon coupon = userCouponMapper.selectByOrderIdForUpdate(orderId);
        if (coupon == null || coupon.getStatus() != CouponConstants.USER_COUPON_LOCKED) {
            throw invalid("只有已锁定的优惠券可以释放");
        }
        if (userCouponMapper.clearOrderStatus(orderId, CouponConstants.USER_COUPON_LOCKED,
                CouponConstants.USER_COUPON_UNUSED) != 1) {
            throw invalid("只有已锁定的优惠券可以释放");
        }
        addUnusedIndex(coupon, couponTemplateMapper.selectById(coupon.getTemplateId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long orderId) {
        UserCoupon coupon = userCouponMapper.selectByOrderIdForUpdate(orderId);
        if (coupon == null || coupon.getStatus() != CouponConstants.USER_COUPON_USED) {
            throw invalid("只有已核销的优惠券可以退款退回");
        }
        CouponTemplate template = couponTemplateMapper.selectById(coupon.getTemplateId());
        LocalDateTime now = LocalDateTime.now();
        boolean stillEffective = template != null
                && template.getStatus() == CouponConstants.TEMPLATE_ENABLED
                && !now.isBefore(template.getValidStartTime())
                && now.isBefore(template.getValidEndTime());
        coupon.setStatus(stillEffective
                ? CouponConstants.USER_COUPON_UNUSED
                : CouponConstants.USER_COUPON_EXPIRED);
        coupon.setUpdatedAt(now);
        clearOrder(coupon);
        userCouponMapper.updateById(coupon);
        if (stillEffective) {
            addUnusedIndex(coupon, template);
            putUserCouponCache(coupon);
        } else {
            deleteUserCouponCache(coupon.getId());
        }
    }

    private void clearOrder(UserCoupon coupon) {
        coupon.setOrderId(null);
        coupon.setServiceFeeAmount(null);
        coupon.setDiscountAmount(null);
        coupon.setLockedAt(null);
        coupon.setUsedAt(null);
    }

    private void addUnusedIndex(UserCoupon coupon, CouponTemplate template) {
        if (userCouponIndexCacheService != null) {
            userCouponIndexCacheService.addUnused(coupon, template);
        }
    }

    private void addCacheAfterCommit(UserCoupon coupon, CouponTemplate template) {
        Runnable task = () -> {
            addUnusedIndex(coupon, template);
            putUserCouponCache(coupon);
            if (couponExpireProducer != null && template != null) {
                try {
                    couponExpireProducer.sendExpireMessage(coupon, template);
                } catch (RuntimeException ex) {
                    log.warn("Send coupon expire message failed, userCouponId={}", coupon.getId(), ex);
                }
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private void removeUnusedIndex(Integer userType, Long userId, Long userCouponId) {
        if (userCouponIndexCacheService != null) {
            userCouponIndexCacheService.remove(userType, userId, userCouponId);
        }
    }

    private CouponTemplate requireEffectiveTemplate(Long templateId, Integer userType) {
        CouponTemplate template = couponTemplateMapper.selectById(templateId);
        LocalDateTime now = LocalDateTime.now();
        if (template == null || template.getStatus() != CouponConstants.TEMPLATE_ENABLED
                || (template.getApplicableUserType() != CouponConstants.APPLICABLE_USER_ALL
                && !userType.equals(template.getApplicableUserType()))
                || now.isBefore(template.getValidStartTime()) || !now.isBefore(template.getValidEndTime())) {
            throw invalid("优惠券不存在或已失效");
        }
        return template;
    }

    private List<UserCouponVO> toVOList(List<UserCoupon> userCoupons) {
        if (userCoupons.isEmpty()) {
            return List.of();
        }
        List<Long> templateIds = userCoupons.stream().map(UserCoupon::getTemplateId).distinct().toList();
        Map<Long, CouponTemplate> templates = couponTemplateCacheService == null
                ? new HashMap<>()
                : new HashMap<>(couponTemplateCacheService.getBatch(templateIds));
        List<Long> missedIds = templateIds.stream().filter(id -> !templates.containsKey(id)).toList();
        if (!missedIds.isEmpty()) {
            List<CouponTemplate> missed = couponTemplateMapper.selectBatchIds(missedIds);
            if (couponTemplateCacheService != null) {
                couponTemplateCacheService.putBatch(missed);
            }
            missed.forEach(item -> templates.put(item.getId(), item));
        }
        return userCoupons.stream()
                .filter(item -> templates.containsKey(item.getTemplateId()))
                .map(item -> toVO(item, templates.get(item.getTemplateId())))
                .toList();
    }

    private UserCouponVO toVO(UserCoupon coupon, CouponTemplate template) {
        UserCouponVO vo = new UserCouponVO();
        vo.setId(coupon.getId());
        vo.setTemplateId(template.getId());
        vo.setUserType(coupon.getUserType());
        vo.setName(template.getName());
        vo.setType(template.getType());
        vo.setScopeType(template.getScopeType());
        vo.setProvinceCode(template.getProvinceCode());
        vo.setCityCode(template.getCityCode());
        vo.setDistrictCode(template.getDistrictCode());
        vo.setPolicyCode(template.getPolicyCode());
        vo.setThresholdAmount(template.getThresholdAmount());
        vo.setDiscountAmount(template.getDiscountAmount());
        vo.setDiscountRate(template.getDiscountRate());
        vo.setMaxDiscountAmount(template.getMaxDiscountAmount());
        vo.setStatus(coupon.getStatus());
        vo.setValidStartTime(template.getValidStartTime());
        vo.setValidEndTime(template.getValidEndTime());
        vo.setReceivedAt(coupon.getReceivedAt());
        return vo;
    }

    private BigDecimal calculate(UserCouponVO coupon, BigDecimal serviceFeeAmount) {
        CouponTemplate template = new CouponTemplate();
        template.setType(coupon.getType());
        template.setThresholdAmount(coupon.getThresholdAmount());
        template.setDiscountAmount(coupon.getDiscountAmount());
        template.setDiscountRate(coupon.getDiscountRate());
        template.setMaxDiscountAmount(coupon.getMaxDiscountAmount());
        return discountCalculator.calculate(template, serviceFeeAmount);
    }

    private boolean matchRegion(UserCouponVO coupon, String provinceCode, String cityCode, String districtCode) {
        if (coupon.getScopeType() == null || coupon.getScopeType() == CouponConstants.SCOPE_PLATFORM) {
            return true;
        }
        return matchNullable(coupon.getProvinceCode(), provinceCode)
                && matchNullable(coupon.getCityCode(), cityCode)
                && matchNullable(coupon.getDistrictCode(), districtCode);
    }

    private void requireRegion(CouponTemplate template, String provinceCode, String cityCode, String districtCode) {
        if (template.getScopeType() == null || template.getScopeType() == CouponConstants.SCOPE_PLATFORM) {
            return;
        }
        if (!matchNullable(template.getProvinceCode(), provinceCode)
                || !matchNullable(template.getCityCode(), cityCode)
                || !matchNullable(template.getDistrictCode(), districtCode)) {
            throw invalid("该优惠券不适用当前城市或区域");
        }
    }

    private boolean matchNullable(String expected, String actual) {
        return expected == null || expected.isBlank() || Objects.equals(expected, actual);
    }

    private String requestId(String requestId) {
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }

    private void putUserCouponCache(UserCoupon coupon) {
        if (userCouponCacheService != null) {
            userCouponCacheService.put(coupon);
        }
    }

    private void putUserCouponCacheBatch(List<UserCoupon> coupons) {
        if (userCouponCacheService != null) {
            userCouponCacheService.putBatch(coupons);
        }
    }

    private void deleteUserCouponCache(Long userCouponId) {
        if (userCouponCacheService != null) {
            userCouponCacheService.delete(userCouponId);
        }
    }

    private void requireUser(Long userId, Integer userType) {
        if (userId == null) {
            throw new ServiceException("缺少登录用户信息", ResultCode.TOKEN_INVALID.getCode());
        }
        if (userType == null || (userType != CouponConstants.USER_TYPE_TENANT
                && userType != CouponConstants.USER_TYPE_LANDLORD)) {
            throw new ServiceException("用户身份只能是租客或房东", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
    }

    private ServiceException invalid(String message) {
        return new ServiceException(message, ResultCode.INVALID_PARA.getCode());
    }
}
