package com.anyujia.anyujiachatapi.domain.dto;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class ChatFrameDTO<T> {
    @NotNull
    @Min(1)
    private Integer version;
    @NotBlank
    private String requestId;
    @NotBlank
    private String type;
    private T data;

    public ChatFrameDTO() {
    }

    public ChatFrameDTO(String type, T data) {
        this(1, null, type, data);
    }

    public ChatFrameDTO(Integer version, String requestId, String type, T data) {
        this.version = version;
        this.requestId = requestId;
        this.type = type;
        this.data = data;
    }
}
