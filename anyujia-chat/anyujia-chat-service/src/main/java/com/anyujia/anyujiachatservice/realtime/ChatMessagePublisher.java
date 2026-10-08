package com.anyujia.anyujiachatservice.realtime;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;

public interface ChatMessagePublisher {
    void route(Long recipientId, MessageVO message);
}
