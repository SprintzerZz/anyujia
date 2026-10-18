package com.anyujia.anyujiachatservice.mq.event;

import com.anyujia.anyujiachatapi.domain.dto.MessageSendReqDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessagePersistEvent {
    private Long senderId;
    private String requestId;
    private MessageSendReqDTO request;
}
