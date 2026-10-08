package com.anyujia.anyujiachatapi.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConversationCreateReqDTO {
    private Long counterpartyUserId;
    @NotNull
    private Long housePublishId;
}
