package com.anyujia.anyujiachatservice.service;

import com.anyujia.anyujiachatservice.domain.entity.ChatConversationMember;

public interface ConversationMemberService {
    ChatConversationMember requireMember(Long conversationId, Long userId);
    Long requireCounterparty(Long conversationId, Long userId);
}
