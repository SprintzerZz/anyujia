package com.anyujia.anyujiachatapi.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class MessageVO {
    private String messageId;
    private String clientMessageId;
    private String conversationSeq;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromId;
    private String content;
    private Integer type;
    private Integer status;
    private Integer visited;
    private Long createTime;
}
