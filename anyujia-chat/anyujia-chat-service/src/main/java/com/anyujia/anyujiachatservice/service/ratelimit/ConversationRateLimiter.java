package com.anyujia.anyujiachatservice.service.ratelimit;

public interface ConversationRateLimiter {
    boolean tryAcquire(Long conversationId);
}
