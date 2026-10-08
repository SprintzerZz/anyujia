package com.anyujia.anyujiaadminapi.app.user.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ChatParticipantIdentityVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantUserId;
    private boolean tenantActive;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long landlordUserId;
    private boolean landlordActive;
    private boolean landlordVerified;
}
