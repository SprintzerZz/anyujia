package com.anyujia.anyujiachatservice.sharding;

/** Explicit chat-message storage state. */
public enum ChatMessageStorageMode {
    LEGACY_ONLY,
    DUAL_WRITE,
    SHARD_ONLY
}
