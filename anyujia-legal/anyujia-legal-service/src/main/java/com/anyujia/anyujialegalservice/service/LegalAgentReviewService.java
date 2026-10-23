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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LegalAgentReviewService {
    private static final long REVIEW_LEASE_MINUTES = 15L;
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
        review.setAttemptCount(review.getAttemptCount() == null ? 0 : review.getAttemptCount());
        review.setLeaseOwner(null);
        review.setLeaseUntil(null);
        review.setUpdatedAt(LocalDateTime.now());
        if (review.getId() == null) {
            reviewMapper.insert(review);
        } else {
            reviewMapper.updateById(review);
        }
        eventService.append(taskId, tenantId, "approval_required", reason);
    }

    @Transactional
    public Optional<LegalAgentReview> claimNext(Long reviewerId) {
        String reviewer = String.valueOf(reviewerId);
        LegalAgentReview candidate = reviewMapper.selectOne(new LambdaQueryWrapper<LegalAgentReview>()
                .eq(LegalAgentReview::getStatus, "PENDING")
                .orderByAsc(LegalAgentReview::getCreatedAt)
                .last("LIMIT 1"));
        if (candidate == null || reviewMapper.claim(candidate.getId(), reviewer,
                LocalDateTime.now().plusMinutes(REVIEW_LEASE_MINUTES)) == 0) {
            return Optional.empty();
        }
        candidate.setStatus("CLAIMED");
        candidate.setLeaseOwner(reviewer);
        candidate.setLeaseUntil(LocalDateTime.now().plusMinutes(REVIEW_LEASE_MINUTES));
        return Optional.of(candidate);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${legal.agent.review-recovery-delay-millis:30000}")
    public void reclaimExpired() {
        reviewMapper.reclaimExpired(LocalDateTime.now());
    }

    @Transactional
    public void approve(Long taskId, Long reviewerId, String comment) {
        LegalAgentReview review = claimed(taskId, reviewerId);
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
        LegalAgentReview review = claimed(taskId, reviewerId);
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

    private LegalAgentReview claimed(Long taskId, Long reviewerId) {
        LegalAgentReview review = reviewMapper.selectOne(new LambdaQueryWrapper<LegalAgentReview>()
                .eq(LegalAgentReview::getTaskId, taskId)
                .eq(LegalAgentReview::getStatus, "CLAIMED")
                .eq(LegalAgentReview::getLeaseOwner, String.valueOf(reviewerId)));
        if (review == null || review.getLeaseUntil() == null || review.getLeaseUntil().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("审核记录不存在、未领取或租约已过期");
        }
        return review;
    }
}
