package com.anyujia.anyujiachatapi.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConversationGetReqDTO {
    @NotNull
    private Long counterpartyUserId;
    @NotNull
    private Long housePublishId;
}
