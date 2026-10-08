package com.anyujia.anyujiachatservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anyujia.anyujiachatapi.domain.dto.MessageSendReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageSyncReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiachatservice.cache.RecentMessageCache;
import com.anyujia.anyujiachatservice.domain.entity.ChatConversation;
import com.anyujia.anyujiachatservice.domain.entity.ChatMessage;
import com.anyujia.anyujiachatservice.mapper.ChatConversationMapper;
import com.anyujia.anyujiachatservice.mapper.ChatConversationMemberMapper;
import com.anyujia.anyujiachatservice.mapper.ChatMessageMapper;
import com.anyujia.anyujiachatservice.mapper.ChatUserConversationMapper;
import com.anyujia.anyujiachatservice.metrics.ChatMetrics;
import com.anyujia.anyujiachatservice.realtime.ChatMessagePublisher;
import com.anyujia.anyujiachatservice.service.impl.MessageServiceImpl;
import com.anyujia.anyujiachatservice.service.moderation.ChatContentFilterProperties;
import com.anyujia.anyujiachatservice.service.moderation.ChatContentModerationService;
import com.anyujia.anyujiachatservice.service.moderation.DefaultChatContentModerationService;
import com.anyujia.anyujiachatservice.service.ratelimit.ConversationRateLimiter;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MessageServiceImplTest {
    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "chat-test");
        TableInfoHelper.initTableInfo(assistant, ChatConversation.class);
        TableInfoHelper.initTableInfo(assistant, ChatMessage.class);
    }

    @Test
    void senderAlwaysComesFromAuthenticatedUser() {
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        ChatConversationMapper conversationMapper = mock(ChatConversationMapper.class);
        ChatConversationMemberMapper memberMapper = mock(ChatConversationMemberMapper.class);
        ChatUserConversationMapper userConversationMapper = mock(ChatUserConversationMapper.class);
        ConversationMemberService memberService = mock(ConversationMemberService.class);
        ChatMessagePublisher pushRouter = mock(ChatMessagePublisher.class);
        RecentMessageCache recentMessageCache = mock(RecentMessageCache.class);
        ChatContentModerationService contentModerationService = content -> content;
        ConversationRateLimiter rateLimiter = ignored -> true;
        MessageService service = new MessageServiceImpl(
                messageMapper, conversationMapper, memberMapper, userConversationMapper,
                memberService, pushRouter, recentMessageCache, contentModerationService,
                rateLimiter, new ChatMetrics(new SimpleMeterRegistry()));

        when(memberService.requireCounterparty(7L, 10L)).thenReturn(20L);
        when(conversationMapper.selectNextMessageSeqForUpdate(7L)).thenReturn(40L);
        when(conversationMapper.updateNextMessageSeq(7L, 41L)).thenReturn(1);
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(messageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(100L);
            return 1;
        });
        when(memberMapper.incrementUnread(7L, 20L)).thenReturn(1);
        when(userConversationMapper.touchMessage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(1);

        MessageSendReqDTO request = new MessageSendReqDTO();
        request.setSessionId(7L);
        request.setClientMessageId("client-1");
        request.setType(0);
        request.setContent("hello");
        MessageVO result = service.send(10L, request);

        ArgumentCaptor<ChatMessage> inserted = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getSenderId()).isEqualTo(10L);
        assertThat(inserted.getValue().getRecipientId()).isEqualTo(20L);
        assertThat(inserted.getValue().getConversationSeq()).isEqualTo(41L);
        assertThat(result.getFromId()).isEqualTo(10L);
        assertThat(result.getConversationSeq()).isEqualTo("41");
        verify(pushRouter).route(20L, result);
        verify(userConversationMapper).touchMessage(10L, 7L, 100L, 41L, result.getCreateTime(), 0);
        verify(userConversationMapper).touchMessage(20L, 7L, 100L, 41L, result.getCreateTime(), 1);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void reconnectSyncReturnsMessagesAfterCursorInAscendingOrder() {
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        ChatConversationMapper conversationMapper = mock(ChatConversationMapper.class);
        ChatConversationMemberMapper memberMapper = mock(ChatConversationMemberMapper.class);
        ChatUserConversationMapper userConversationMapper = mock(ChatUserConversationMapper.class);
        ConversationMemberService memberService = mock(ConversationMemberService.class);
        ChatMessagePublisher pushRouter = mock(ChatMessagePublisher.class);
        RecentMessageCache recentMessageCache = mock(RecentMessageCache.class);
        ChatContentModerationService contentModerationService = content -> content;
        ConversationRateLimiter rateLimiter = ignored -> true;
        MessageService service = new MessageServiceImpl(
                messageMapper, conversationMapper, memberMapper, userConversationMapper,
                memberService, pushRouter, recentMessageCache, contentModerationService,
                rateLimiter, new ChatMetrics(new SimpleMeterRegistry()));
        ChatMessage first = message(101L, "first");
        ChatMessage second = message(102L, "second");
        when(messageMapper.selectList(any())).thenReturn(List.of(first, second));
        MessageSyncReqDTO request = new MessageSyncReqDTO();
        request.setSessionId(7L);
        request.setAfterConversationSeq("100");
        request.setCount(2);

        List<MessageVO> result = service.sync(10L, request);

        verify(memberService).requireMember(7L, 10L);
        ArgumentCaptor<LambdaQueryWrapper> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(messageMapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment())
                .contains("conversation_seq >")
                .contains("ORDER BY conversation_seq ASC");
        assertThat(result).extracting(MessageVO::getMessageId).containsExactly("101", "102");
    }

    @Test
    void sendModeratesSensitiveContentBeforePersistence() {
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        ChatConversationMapper conversationMapper = mock(ChatConversationMapper.class);
        ChatConversationMemberMapper memberMapper = mock(ChatConversationMemberMapper.class);
        ChatUserConversationMapper userConversationMapper = mock(ChatUserConversationMapper.class);
        ConversationMemberService memberService = mock(ConversationMemberService.class);
        ChatMessagePublisher pushRouter = mock(ChatMessagePublisher.class);
        RecentMessageCache recentMessageCache = mock(RecentMessageCache.class);
        ChatContentFilterProperties properties = new ChatContentFilterProperties();
        properties.setSensitiveWords(List.of("押金不退"));
        ChatContentModerationService contentModerationService =
                new DefaultChatContentModerationService(properties);
        ConversationRateLimiter rateLimiter = ignored -> true;
        MessageService service = new MessageServiceImpl(
                messageMapper, conversationMapper, memberMapper, userConversationMapper,
                memberService, pushRouter, recentMessageCache, contentModerationService,
                rateLimiter, new ChatMetrics(new SimpleMeterRegistry()));

        when(memberService.requireCounterparty(7L, 10L)).thenReturn(20L);
        when(conversationMapper.selectNextMessageSeqForUpdate(7L)).thenReturn(40L);
        when(conversationMapper.updateNextMessageSeq(7L, 41L)).thenReturn(1);
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(messageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(100L);
            return 1;
        });
        when(memberMapper.incrementUnread(7L, 20L)).thenReturn(1);
        when(userConversationMapper.touchMessage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(1);

        MessageSendReqDTO request = new MessageSendReqDTO();
        request.setSessionId(7L);
        request.setClientMessageId("client-1");
        request.setType(0);
        request.setContent("押金不退，别问了");

        MessageVO result = service.send(10L, request);

        ArgumentCaptor<ChatMessage> inserted = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getContent()).isEqualTo("***，别问了");
        assertThat(result.getContent()).isEqualTo("***，别问了");
    }

    private ChatMessage message(Long id, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setConversationId(7L);
        message.setConversationSeq(id);
        message.setSenderId(20L);
        message.setRecipientId(10L);
        message.setType(0);
        message.setContent(content);
        message.setStatus(0);
        message.setVisited(10);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }
}
