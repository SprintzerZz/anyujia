package com.anyujia.anyujialegalapi.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LegalAgentBindReqDTO {
    @NotNull
    private Long tenantId;
    @NotNull
    private Long chatConversationId;
    @NotNull
    private Long landlordConversationId;
    @NotNull
    private Long contractId;
}
