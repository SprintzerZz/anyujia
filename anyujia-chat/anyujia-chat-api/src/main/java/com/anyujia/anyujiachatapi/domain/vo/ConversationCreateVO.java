package com.anyujia.anyujiachatapi.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ConversationCreateVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;
    private ChatUserVO loginUser;
    private ChatUserVO otherUser;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long houseId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long housePublishId;
}
