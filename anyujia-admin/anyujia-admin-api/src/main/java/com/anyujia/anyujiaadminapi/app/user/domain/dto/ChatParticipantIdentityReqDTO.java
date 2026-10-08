package com.anyujia.anyujiaadminapi.app.user.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatParticipantIdentityReqDTO {
    @NotNull
    private Long tenantUserId;

    @NotNull
    private Long landlordUserId;
}
