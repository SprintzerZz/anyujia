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
            + "lease_owner=NULL, lease_until=NULL, error_message=NULL, "
            + "completed_at=CURRENT_TIMESTAMP(3), updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{taskId} AND status='RUNNING' AND lease_owner=#{workerId}")
    int complete(@Param("taskId") Long taskId, @Param("workerId") String workerId,
                 @Param("answerJson") String answerJson);

    @Update("UPDATE legal_agent_task SET status='FAILED', error_message=#{errorMessage}, "
            + "lease_owner=NULL, lease_until=NULL, updated_at=CURRENT_TIMESTAMP(3) "
            + "WHERE id=#{taskId} AND status='RUNNING' AND lease_owner=#{workerId}")
    int fail(@Param("taskId") Long taskId, @Param("workerId") String workerId,
             @Param("errorMessage") String errorMessage);
}
