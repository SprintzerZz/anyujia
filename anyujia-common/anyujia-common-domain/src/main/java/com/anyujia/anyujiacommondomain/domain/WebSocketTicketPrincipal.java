package com.anyujia.anyujiacommondomain.domain;

public record WebSocketTicketPrincipal(
        String userId,
        String userName,
        String userFrom,
        String userType) {
}
