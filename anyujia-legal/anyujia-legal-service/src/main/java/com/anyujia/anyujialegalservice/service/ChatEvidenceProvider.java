package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import java.util.List;

public interface ChatEvidenceProvider {
    List<MessageVO> recentMessages(Long tenantId, Long conversationId, int limit);

    /** Bypass cached windows for the final consistency check. */
    default List<MessageVO> freshMessages(Long tenantId, Long conversationId, int limit) {
        return recentMessages(tenantId, conversationId, limit);
    }
}
