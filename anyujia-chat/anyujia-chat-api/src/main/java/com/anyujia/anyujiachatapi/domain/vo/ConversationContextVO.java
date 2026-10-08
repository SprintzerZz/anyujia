package com.anyujia.anyujiachatapi.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ConversationContextVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantUserId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long landlordUserId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long houseId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long housePublishId;
    private Integer status;
}
