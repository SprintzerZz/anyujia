package com.anyujia.anyujialegalservice.mapper;

import com.anyujia.anyujialegalservice.domain.entity.LegalTraceEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;

public interface LegalTraceEventMapper extends BaseMapper<LegalTraceEvent> {
    @Insert("INSERT IGNORE INTO legal_trace_event_archive "
            + "(id, job_id, stage, status, started_at, ended_at, duration_millis, details_json, created_at) "
            + "SELECT id, job_id, stage, status, started_at, ended_at, duration_millis, details_json, created_at "
            + "FROM legal_trace_event WHERE created_at < #{cutoff} ORDER BY id LIMIT #{limit}")
    int archiveBefore(LocalDateTime cutoff, int limit);

    @Delete("DELETE FROM legal_trace_event WHERE id IN "
            + "(SELECT id FROM legal_trace_event_archive WHERE created_at < #{cutoff} LIMIT #{limit})")
    int deleteArchivedBefore(LocalDateTime cutoff, int limit);
}
