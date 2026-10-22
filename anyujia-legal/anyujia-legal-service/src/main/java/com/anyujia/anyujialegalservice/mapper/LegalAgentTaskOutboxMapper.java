package com.anyujia.anyujialegalservice.mapper;

import com.anyujia.anyujialegalservice.domain.entity.LegalAgentTaskOutbox;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface LegalAgentTaskOutboxMapper extends BaseMapper<LegalAgentTaskOutbox> {
    @Update("UPDATE legal_agent_task_outbox SET status=1, claim_token=#{token}, "
            + "updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status=0 "
            + "AND next_retry_at <= CURRENT_TIMESTAMP(3)")
    int claim(@Param("id") Long id, @Param("token") String token);

    @Update("UPDATE legal_agent_task_outbox SET status=2, claim_token=NULL, "
            + "updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status=1 AND claim_token=#{token}")
    int markSent(@Param("id") Long id, @Param("token") String token);

    @Update("UPDATE legal_agent_task_outbox SET status=0, retry_count=retry_count+1, "
            + "claim_token=NULL, next_retry_at=#{nextRetryAt}, updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{id} AND status=1 AND claim_token=#{token}")
    int retry(@Param("id") Long id, @Param("token") String token,
              @Param("nextRetryAt") java.time.LocalDateTime nextRetryAt);

    @Update("UPDATE legal_agent_task_outbox SET status=3, claim_token=NULL, "
            + "updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status=1 AND claim_token=#{token}")
    int markFailed(@Param("id") Long id, @Param("token") String token);

    @Update("UPDATE legal_agent_task_outbox SET status=0, claim_token=NULL, "
            + "next_retry_at=CURRENT_TIMESTAMP(3), updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE status=1 AND updated_at < #{deadline}")
    int reclaim(@Param("deadline") java.time.LocalDateTime deadline);
}
