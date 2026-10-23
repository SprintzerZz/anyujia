package com.anyujia.anyujialegalservice.mapper;

import com.anyujia.anyujialegalservice.domain.entity.LegalAgentReview;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface LegalAgentReviewMapper extends BaseMapper<LegalAgentReview> {
    @Update("UPDATE legal_agent_review SET status='CLAIMED', attempt_count=attempt_count+1, "
            + "lease_owner=#{reviewerId}, lease_until=#{leaseUntil}, updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{reviewId} AND status='PENDING'")
    int claim(@Param("reviewId") Long reviewId, @Param("reviewerId") String reviewerId,
              @Param("leaseUntil") java.time.LocalDateTime leaseUntil);

    @Update("UPDATE legal_agent_review SET status='PENDING', lease_owner=NULL, lease_until=NULL, "
            + "updated_at=CURRENT_TIMESTAMP(3) WHERE status='CLAIMED' AND lease_until < #{deadline}")
    int reclaimExpired(@Param("deadline") java.time.LocalDateTime deadline);
}
