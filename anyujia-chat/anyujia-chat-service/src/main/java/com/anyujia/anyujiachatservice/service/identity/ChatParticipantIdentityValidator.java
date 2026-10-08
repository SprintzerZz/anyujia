package com.anyujia.anyujiachatservice.service.identity;

public interface ChatParticipantIdentityValidator {
    void validate(Long tenantUserId, Long landlordUserId);
}
