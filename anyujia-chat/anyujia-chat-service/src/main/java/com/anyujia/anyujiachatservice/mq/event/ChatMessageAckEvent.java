package com.anyujia.anyujiachatservice.mq.event;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageAckEvent {
    private Long senderId;
    private String requestId;
    private MessageVO message;
}
