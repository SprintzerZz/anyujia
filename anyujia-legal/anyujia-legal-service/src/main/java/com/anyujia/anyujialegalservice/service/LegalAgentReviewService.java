package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.domain.entity.LegalAgentReview;
import com.anyujia.anyujialegalservice.mapper.LegalAgentReviewMapper;
import com.anyujia.anyujialegalservice.mapper.LegalAgentTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LegalAgentReviewService {
    private final LegalAgentReviewMapper reviewMapper;
    private final LegalAgentTaskMapper taskMapper;
    private final LegalAgentTaskEventService eventService;
    private final LegalAgentAnswerDeliveryService answerDeliveryService;

    @Transactional
    public void create(Long taskId, Long tenantId, String reason, BigDecimal supportScore, String answerJson) {
        LegalAgentReview review = reviewMapper.selectOne(new LambdaQueryWrapper<LegalAgentReview>()
                .eq(LegalAgentReview::getTaskId, taskId));
        if (review == null) {
            review = new LegalAgentReview();
            review.setTaskId(taskId);
            review.setTenantId(tenantId);
            review.setCreatedAt(LocalDateTime.now());
        }
        review.setStatus("PENDING");
        review.setReason(reason);
        review.setSupportScore(supportScore);
        review.setAnswerJson(answerJson);
        review.setReviewedAt(null);
        review.setReviewerId(null);
        review.setReviewerComment(null);
        review.setUpdatedAt(LocalDateTime.now());
        if (review.getId() == null) {
            reviewMapper.insert(review);
        } else {
            reviewMapper.updateById(review);
        }
        eventService.append(taskId, tenantId, "approval_required", reason);
    }

    @Transactional
    public void approve(Long taskId, Long reviewerId, String comment) {
        LegalAgentReview review = pending(taskId);
        if (taskMapper.approveReview(taskId) == 0) {
            throw new IllegalStateException("法律助手任务当前不可审核通过");
        }
        review.setStatus("APPROVED");
        review.setReviewerId(reviewerId);
        review.setReviewerComment(comment);
        review.setReviewedAt(LocalDateTime.now());
        review.setUpdatedAt(review.getReviewedAt());
        reviewMapper.updateById(review);
        eventService.append(taskId, review.getTenantId(), "result", review.getAnswerJson());
        answerDeliveryService.schedule(taskId);
    }

    @Transactional
    public void reject(Long taskId, Long reviewerId, String comment) {
        LegalAgentReview review = pending(taskId);
        if (taskMapper.rejectReview(taskId, comment == null ? "人工审核未通过" : comment) == 0) {
            throw new IllegalStateException("法律助手任务当前不可审核拒绝");
        }
        review.setStatus("REJECTED");
        review.setReviewerId(reviewerId);
        review.setReviewerComment(comment);
        review.setReviewedAt(LocalDateTime.now());
        review.setUpdatedAt(review.getReviewedAt());
        reviewMapper.updateById(review);
        eventService.append(taskId, review.getTenantId(), "failed", comment == null ? "人工审核未通过" : comment);
    }

    private LegalAgentReview pending(Long taskId) {
        LegalAgentReview review = reviewMapper.selectOne(new LambdaQueryWrapper<LegalAgentReview>()
                .eq(LegalAgentReview::getTaskId, taskId)
                .eq(LegalAgentReview::getStatus, "PENDING"));
        if (review == null) {
            throw new IllegalStateException("待审核记录不存在");
        }
        return review;
    }
}
