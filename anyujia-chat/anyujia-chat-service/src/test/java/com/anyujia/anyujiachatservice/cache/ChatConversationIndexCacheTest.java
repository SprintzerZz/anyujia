package com.anyujia.anyujiachatservice.cache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation;
import com.anyujia.anyujiacommonredis.service.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatConversationIndexCacheTest {
    @Test
    void flushedNewConversationInvalidatesListLoadedBeforeMysqlCaughtUp() {
        RedisService redis = mock(RedisService.class);
        ChatConversationIndexCache cache = new ChatConversationIndexCache(redis, new ObjectMapper());
        when(redis.hasKey("chat:conversation:list:2")).thenReturn(true);
        when(redis.hasKey("chat:conversation:list:loaded:2")).thenReturn(true);
        when(redis.getCacheList(eq("chat:conversation:list:2"), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(List.of());

        cache.markFlushed(2L, 10L, new ChatConversationIndexCache.IndexUpdate(100L, 1L, 123L));

        verify(redis).deleteObject("chat:conversation:list:loaded:2");
        verify(redis).deleteObject("chat:conversation:list:2");
    }

    @Test
    void flushedExistingConversationKeepsCurrentList() {
        RedisService redis = mock(RedisService.class);
        ChatConversationIndexCache cache = new ChatConversationIndexCache(redis, new ObjectMapper());
        ChatUserConversation item = new ChatUserConversation();
        item.setConversationId(10L);
        item.setLastMessageSeq(2L);
        when(redis.hasKey("chat:conversation:list:loaded:2")).thenReturn(true);
        when(redis.getCacheList(eq("chat:conversation:list:2"), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(List.of(item));

        cache.markFlushed(2L, 10L, new ChatConversationIndexCache.IndexUpdate(100L, 2L, 123L));

        verify(redis, never()).deleteObject("chat:conversation:list:loaded:2");
        verify(redis, never()).deleteObject("chat:conversation:list:2");
    }

    @Test
    void olderOutboxEventCannotMoveCachedConversationBackwards() {
        RedisService redis = mock(RedisService.class);
        ChatConversationIndexCache cache = new ChatConversationIndexCache(redis, new ObjectMapper());
        ChatUserConversation item = new ChatUserConversation();
        item.setConversationId(10L);
        item.setLastMessageId(200L);
        item.setLastMessageSeq(2L);
        item.setLastMessageTime(2000L);
        when(redis.hasKey("chat:conversation:list:loaded:2")).thenReturn(true);
        when(redis.getCacheList(eq("chat:conversation:list:2"), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(new ArrayList<>(List.of(item)));

        cache.touchMessageForUsers(List.of(2L), 10L, 100L, 1L, 1000L);

        org.junit.jupiter.api.Assertions.assertEquals(200L, item.getLastMessageId());
        org.junit.jupiter.api.Assertions.assertEquals(2L, item.getLastMessageSeq());
        org.junit.jupiter.api.Assertions.assertEquals(2000L, item.getLastMessageTime());
    }
}
