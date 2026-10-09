package com.anyujia.anyujiachatservice.service;

import com.anyujia.anyujiachatapi.domain.dto.AgentMessageReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageListReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageReadReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageSendReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageSyncReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageVisitedReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import java.util.List;

public interface MessageService {
    MessageVO send(Long userId, MessageSendReqDTO request);
    MessageVO sendAgentMessage(Long userId, AgentMessageReqDTO request);
    List<MessageVO> list(Long userId, MessageListReqDTO request);
    List<MessageVO> sync(Long userId, MessageSyncReqDTO request);
    void markVisited(Long userId, MessageVisitedReqDTO request);
    void markRead(Long userId, MessageReadReqDTO request);
}
