package com.anyujia.anyujialegalservice.mapper;

import com.anyujia.anyujialegalservice.domain.entity.LegalAgentTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface LegalAgentTaskMapper extends BaseMapper<LegalAgentTask> {
    @Update("UPDATE legal_agent_task SET status='RUNNING', attempt_count=attempt_count+1, "
            + "lease_owner=#{workerId}, lease_until=#{leaseUntil}, updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{taskId} AND status='PENDING'")
    int claim(@Param("taskId") Long taskId, @Param("workerId") String workerId,
              @Param("leaseUntil") java.time.LocalDateTime leaseUntil);

    @Update("UPDATE legal_agent_task SET status='COMPLETED', answer_json=#{answerJson}, "
            + "context_hash=#{contextHash}, context_status=#{contextStatus}, "
            + "context_conflict=#{contextConflict}, "
            + "evidence_manifest_json=#{evidenceManifestJson}, "
            + "lease_owner=NULL, lease_until=NULL, error_message=NULL, "
            + "completed_at=CURRENT_TIMESTAMP(3), updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{taskId} AND status='RUNNING' AND lease_owner=#{workerId}")
    int complete(@Param("taskId") Long taskId, @Param("workerId") String workerId,
                 @Param("answerJson") String answerJson,
                 @Param("contextHash") String contextHash,
                 @Param("contextStatus") String contextStatus,
                 @Param("contextConflict") boolean contextConflict,
                 @Param("evidenceManifestJson") String evidenceManifestJson);

    @Update("UPDATE legal_agent_task SET status='FAILED', error_message=#{errorMessage}, "
            + "lease_owner=NULL, lease_until=NULL, updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{taskId} AND status='RUNNING' AND lease_owner=#{workerId}")
    int fail(@Param("taskId") Long taskId, @Param("workerId") String workerId,
             @Param("errorMessage") String errorMessage);

    @Update("UPDATE legal_agent_task SET status='WAITING_REVIEW', answer_json=#{answerJson}, "
            + "context_hash=#{contextHash}, context_status=#{contextStatus}, context_conflict=#{contextConflict}, "
            + "evidence_manifest_json=#{evidenceManifestJson}, review_required=1, lease_owner=NULL, lease_until=NULL, "
            + "updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{taskId} AND status='RUNNING' AND lease_owner=#{workerId}")
    int holdForReview(@Param("taskId") Long taskId, @Param("workerId") String workerId,
                      @Param("answerJson") String answerJson, @Param("contextHash") String contextHash,
                      @Param("contextStatus") String contextStatus, @Param("contextConflict") boolean contextConflict,
                      @Param("evidenceManifestJson") String evidenceManifestJson);

    @Update("UPDATE legal_agent_task SET status='PENDING', review_required=0, error_message=NULL, "
            + "lease_owner=NULL, lease_until=NULL, updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{taskId} AND status='FAILED'")
    int replay(@Param("taskId") Long taskId);

    @Update("UPDATE legal_agent_task SET status='COMPLETED', review_required=0, completed_at=CURRENT_TIMESTAMP(3), "
            + "updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{taskId} AND status='WAITING_REVIEW'")
    int approveReview(@Param("taskId") Long taskId);

    @Update("UPDATE legal_agent_task SET status='FAILED', review_required=0, error_message=#{errorMessage}, "
            + "updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{taskId} AND status='WAITING_REVIEW'")
    int rejectReview(@Param("taskId") Long taskId, @Param("errorMessage") String errorMessage);

    @Update("UPDATE legal_agent_task SET status='PENDING', lease_owner=NULL, lease_until=NULL, "
            + "updated_at=CURRENT_TIMESTAMP(3) WHERE status='RUNNING' AND lease_until < #{deadline}")
    int reclaim(@Param("deadline") java.time.LocalDateTime deadline);

    @Update("UPDATE legal_agent_task SET chat_delivery_state=1, "
            + "chat_delivery_claimed_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{taskId} AND status='COMPLETED' AND context_conflict=0 "
            + "AND chat_delivery_state=0")
    int claimChatDelivery(@Param("taskId") Long taskId);

    @Update("UPDATE legal_agent_task SET chat_delivery_state=2, "
            + "chat_delivery_claimed_at=NULL WHERE id=#{taskId} AND chat_delivery_state=1")
    int markChatDelivered(@Param("taskId") Long taskId);

    @Update("UPDATE legal_agent_task SET chat_delivery_state=0, "
            + "chat_delivery_claimed_at=NULL WHERE id=#{taskId} AND chat_delivery_state=1")
    int retryChatDelivery(@Param("taskId") Long taskId);

    @Update("UPDATE legal_agent_task SET chat_delivery_state=0, "
            + "chat_delivery_claimed_at=NULL WHERE status='COMPLETED' AND chat_delivery_state=1 "
            + "AND chat_delivery_claimed_at < #{deadline}")
    int reclaimChatDelivery(@Param("deadline") java.time.LocalDateTime deadline);
}
