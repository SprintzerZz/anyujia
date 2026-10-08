package com.anyujia.anyujiachatapi.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatFrameDTO<T> {
    private String type;
    private T data;
}
