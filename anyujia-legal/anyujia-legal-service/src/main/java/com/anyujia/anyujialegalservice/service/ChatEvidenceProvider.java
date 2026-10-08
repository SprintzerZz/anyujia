package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import java.util.List;

public interface ChatEvidenceProvider {
    List<MessageVO> recentMessages(Long tenantId, Long conversationId, int limit);
}
