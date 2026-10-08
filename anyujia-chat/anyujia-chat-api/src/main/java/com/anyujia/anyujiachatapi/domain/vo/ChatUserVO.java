package com.anyujia.anyujiachatapi.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ChatUserVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private Integer userType;
    private String nickname;
    private String avatarUrl;
}
