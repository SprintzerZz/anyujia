package com.anyujia.anyujiachatapi.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatProtocolErrorCode {
    INVALID_JSON("CHAT-4001", "Invalid JSON"),
    INVALID_FRAME("CHAT-4002", "Invalid chat frame"),
    UNSUPPORTED_VERSION("CHAT-4003", "Unsupported protocol version"),
    UNSUPPORTED_TYPE("CHAT-4004", "Unsupported chat frame type"),
    INVALID_MESSAGE("CHAT-4005", "Invalid chat message"),
    SERVER_BUSY("CHAT-5001", "Chat service is busy");

    private final String code;
    private final String message;
}
