package com.anyujia.anyujialegalservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("legal_trace_event")
public class LegalTraceEvent {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long jobId;
    private String stage;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long durationMillis;
    private String detailsJson;
    private LocalDateTime createdAt;
}
