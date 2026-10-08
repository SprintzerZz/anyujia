package com.anyujia.anyujiachatapi.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConversationHouseReqDTO {
    @NotNull
    private Long sessionId;
    @NotNull
    private Long housePublishId;
}
