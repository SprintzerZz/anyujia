package com.anyujia.anyujiachatapi.domain.dto;

import lombok.Data;

@Data
public class ConversationListReqDTO {
    private Integer count = 20;
    private Long lastSessionTime;
    private String lastSessionId;
}
