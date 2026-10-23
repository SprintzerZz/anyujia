package com.anyujia.anyujiachatapi.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AgentSessionCreateReqDTO {
    @NotNull
    private Long landlordConversationId;
    @NotNull
    private Long contractId;
}
