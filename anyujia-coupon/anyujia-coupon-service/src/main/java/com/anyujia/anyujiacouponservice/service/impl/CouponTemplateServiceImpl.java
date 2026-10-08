package com.anyujia.anyujiacouponservice.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.anyujia.anyujiacouponapi.constants.CouponConstants;
import com.anyujia.anyujiacouponapi.domain.dto.CouponTemplateCreateReqDTO;
import com.anyujia.anyujiacouponapi.domain.dto.CouponTemplateListReqDTO;
import com.anyujia.anyujiacouponapi.domain.vo.CouponTemplateVO;
import com.anyujia.anyujiacouponservice.domain.entity.CouponTemplate;
import com.anyujia.anyujiacouponservice.mapper.CouponTemplateMapper;
import com.anyujia.anyujiacouponservice.service.CouponReceiveCacheService;
import com.anyujia.anyujiacouponservice.service.CouponTemplateCacheService;
import com.anyujia.anyujiacouponservice.service.CouponTemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 优惠券模板服务实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CouponTemplateServiceImpl implements CouponTemplateService {

    private final CouponTemplateMapper couponTemplateMapper;
    private final CouponReceiveCacheService couponReceiveCacheService;
    private final CouponTemplateCacheService couponTemplateCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CouponTemplateCreateReqDTO request) {
        validateRule(request);
        validateScope(request);
        if (!request.getValidStartTime().isBefore(request.getValidEndTime())) {
            throw invalid("生效时间必须早于失效时间");
        }

        CouponTemplate template = new CouponTemplate();
        BeanUtils.copyProperties(request, template);
        if (template.getScopeType() == null) {
            template.setScopeType(CouponConstants.SCOPE_PLATFORM);
        }
        if (template.getGrantMode() == null) {
            template.setGrantMode(CouponConstants.GRANT_MODE_PUBLIC);
        }
        if (template.getReceiveLimit() == null) {
            template.setReceiveLimit(1);
        }
        template.setTotalStock(request.getStock());
        template.setAvailableStock(request.getStock());
        template.setStatus(CouponConstants.TEMPLATE_ENABLED);
        LocalDateTime now = LocalDateTime.now();
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        couponTemplateMapper.insert(template);
        warmCacheAfterCommit(template);
        return template.getId();
    }

    @Override
    public BasePageVO<CouponTemplateVO> list(CouponTemplateListReqDTO request) {
        long pageIndex = request.getPageIndex() == null || request.getPageIndex() < 1 ? 1L : request.getPageIndex();
        long pageSize = request.getPageSize() == null || request.getPageSize() < 1 ? 10L : request.getPageSize();
        Page<CouponTemplate> page = couponTemplateMapper.selectPage(
                new Page<>(pageIndex, Math.min(pageSize, 100L)),
                new LambdaQueryWrapper<CouponTemplate>()
                        .likeRight(StringUtils.hasText(request.getName()), CouponTemplate::getName, request.getName())
                        .eq(request.getType() != null, CouponTemplate::getType, request.getType())
                        .eq(request.getStatus() != null, CouponTemplate::getStatus, request.getStatus())
                        .eq(request.getApplicableUserType() != null, CouponTemplate::getApplicableUserType,
                                request.getApplicableUserType())
                        .orderByDesc(CouponTemplate::getId));

        List<CouponTemplateVO> list = page.getRecords().stream().map(this::toVO).toList();
        BasePageVO<CouponTemplateVO> result = new BasePageVO<>();
        result.setPageIndex((int) pageIndex);
        result.setPageSize((int) Math.min(pageSize, 100L));
        result.setTotal(page.getTotal());
        result.setList(list);
        return result;
    }

    private void validateRule(CouponTemplateCreateReqDTO request) {
        switch (request.getType()) {
            case CouponConstants.TYPE_FIXED -> require(request.getDiscountAmount() != null, "立减券必须配置优惠金额");
            case CouponConstants.TYPE_THRESHOLD -> {
                require(request.getThresholdAmount() != null, "满减券必须配置门槛金额");
                require(request.getDiscountAmount() != null, "满减券必须配置优惠金额");
                require(request.getDiscountAmount().compareTo(request.getThresholdAmount()) < 0,
                        "优惠金额必须小于门槛金额");
            }
            case CouponConstants.TYPE_DISCOUNT -> require(request.getDiscountRate() != null, "折扣券必须配置折扣率");
            default -> throw invalid("优惠券类型仅支持1立减、2满减、3折扣");
        }
    }

    private void validateScope(CouponTemplateCreateReqDTO request) {
        int scopeType = request.getScopeType() == null ? CouponConstants.SCOPE_PLATFORM : request.getScopeType();
        if ((scopeType == CouponConstants.SCOPE_REGION || scopeType == CouponConstants.SCOPE_NATIONAL_SUBSIDY)
                && !StringUtils.hasText(request.getProvinceCode())
                && !StringUtils.hasText(request.getCityCode())
                && !StringUtils.hasText(request.getDistrictCode())) {
            throw invalid("区域券或国补券必须至少配置一个行政区划编码");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw invalid(message);
        }
    }

    private ServiceException invalid(String message) {
        return new ServiceException(message, ResultCode.INVALID_PARA.getCode());
    }

    private void warmCacheAfterCommit(CouponTemplate template) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeWarmCache(template);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeWarmCache(template);
            }
        });
    }

    private void safeWarmCache(CouponTemplate template) {
        try {
            couponReceiveCacheService.warm(template);
            couponTemplateCacheService.put(template);
        } catch (RuntimeException ex) {
            log.warn("Coupon template cache warm-up failed, templateId={}", template.getId(), ex);
        }
    }

    private CouponTemplateVO toVO(CouponTemplate template) {
        CouponTemplateVO vo = new CouponTemplateVO();
        BeanUtils.copyProperties(template, vo);
        return vo;
    }
}
