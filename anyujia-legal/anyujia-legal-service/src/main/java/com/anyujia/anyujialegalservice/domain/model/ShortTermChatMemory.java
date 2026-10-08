package com.anyujia.anyujialegalservice.domain.model;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ShortTermChatMemory implements Serializable {
    private Long tenantId;
    private Long conversationId;
    private Integer limit;
    private Integer messageCount;
    private List<MessageVO> messages;
    private LocalDateTime cachedAt;
}
