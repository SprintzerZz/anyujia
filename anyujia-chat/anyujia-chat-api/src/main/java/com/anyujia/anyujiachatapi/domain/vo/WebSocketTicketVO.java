package com.anyujia.anyujiachatapi.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WebSocketTicketVO {
    private String ticket;
    private Long expiresInSeconds;
}
