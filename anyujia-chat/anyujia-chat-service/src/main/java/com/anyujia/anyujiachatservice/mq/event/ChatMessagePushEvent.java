package com.anyujia.anyujiachatservice.mq.event;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessagePushEvent {
    private Long recipientId;
    private MessageVO message;
    private int attempts;
}
