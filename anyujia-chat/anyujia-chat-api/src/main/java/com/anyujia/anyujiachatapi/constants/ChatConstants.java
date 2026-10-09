package com.anyujia.anyujiachatapi.constants;

public final class ChatConstants {
    public static final int USER_TYPE_TENANT = 1;
    public static final int USER_TYPE_LANDLORD = 2;
    public static final int CONVERSATION_TYPE_LANDLORD_TENANT = 1;
    public static final int CONVERSATION_TYPE_AGENT = 2;
    public static final long AGENT_USER_ID = -1L;
    public static final int CONVERSATION_ACTIVE = 1;
    public static final int MESSAGE_UNREAD = 0;
    public static final int MESSAGE_READ = 1;
    public static final int MESSAGE_NOT_VISITED = 10;
    public static final int MESSAGE_VISITED = 11;
    public static final int MAX_PAGE_SIZE = 100;

    private ChatConstants() {
    }
}
