package com.anyujia.anyujiaadminservice.user.service;

import com.anyujia.anyujiaadminapi.app.user.domain.dto.ChatParticipantIdentityReqDTO;
import com.anyujia.anyujiaadminapi.app.user.domain.vo.ChatParticipantIdentityVO;

public interface IAppUserIdentityService {
    void ensureTenantIdentity(Long appUserId);

    ChatParticipantIdentityVO checkChatParticipants(ChatParticipantIdentityReqDTO request);
}
