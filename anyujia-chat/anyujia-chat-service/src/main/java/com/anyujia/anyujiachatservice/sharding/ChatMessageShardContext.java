package com.anyujia.anyujiachatservice.sharding;

public final class ChatMessageShardContext {
    private static final ThreadLocal<String> TABLE = new ThreadLocal<>();

    private ChatMessageShardContext() {
    }

    public static void use(String tableName) {
        if (tableName == null || !tableName.matches("chat_message_s\\d{2}")) {
            throw new IllegalArgumentException("Invalid chat message shard table");
        }
        TABLE.set(tableName);
    }

    public static String currentTable() {
        return TABLE.get();
    }

    public static void clear() {
        TABLE.remove();
    }
}
