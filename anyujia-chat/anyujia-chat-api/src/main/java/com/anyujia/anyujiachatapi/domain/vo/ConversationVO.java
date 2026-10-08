package com.anyujia.anyujiachatapi.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ConversationVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long houseId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long housePublishId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long lastMessageId;
    private String lastMessageSeq;
    private Long lastSessionTime;
    private Integer notVisitedCount;
    private ChatUserVO otherUser;
}
