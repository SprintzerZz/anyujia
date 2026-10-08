package com.anyujia.anyujiachatapi.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class MessageReadReqDTO {
    @NotNull
    private Long sessionId;
    @NotEmpty
    private List<String> messageIds;
}
