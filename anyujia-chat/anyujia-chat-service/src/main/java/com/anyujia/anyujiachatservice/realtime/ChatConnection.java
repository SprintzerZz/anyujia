package com.anyujia.anyujiachatservice.realtime;

public interface ChatConnection {
    String id();

    Long userId();

    boolean isOpen();

    void touch();

    long lastActivityMillis();

    void send(String payload);

    void close(int code, String reason);

    default boolean managesOwnIdleTimeout() {
        return false;
    }
}
