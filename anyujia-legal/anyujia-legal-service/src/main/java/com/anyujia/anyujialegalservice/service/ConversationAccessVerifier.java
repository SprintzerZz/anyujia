package com.anyujia.anyujialegalservice.service;

public interface ConversationAccessVerifier {
    void requireTenantAccess(Long tenantId, Long conversationId);
}
