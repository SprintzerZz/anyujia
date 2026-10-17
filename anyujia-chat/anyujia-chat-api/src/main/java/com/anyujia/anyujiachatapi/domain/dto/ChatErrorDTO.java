package com.anyujia.anyujiachatapi.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatErrorDTO {
    private String code;
    private String message;
}
