package com.anyujia.anyujialegalapi.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.util.List;
import lombok.Data;

@Data
public class LegalAgentTaskVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    private String requestId;
    private String status;
    private String answerText;
    private List<LegalCitationVO> citations;
    private String mode;
    private boolean degraded;
    private String contextHash;
    private String contextStatus;
    private boolean contextConflict;
    private Boolean reviewRequired;
    private String errorMessage;
    private Long createdAt;
    private Long completedAt;
}
