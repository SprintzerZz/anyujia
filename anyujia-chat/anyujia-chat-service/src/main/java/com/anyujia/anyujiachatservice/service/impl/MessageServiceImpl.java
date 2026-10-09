package com.anyujia.anyujiachatservice.service.impl;

import com.anyujia.anyujiachatapi.constants.ChatConstants;
import com.anyujia.anyujiachatapi.domain.dto.AgentMessageReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageListReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageReadReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageSendReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageSyncReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.MessageVisitedReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujialegalapi.domain.dto.LegalAgentAnswerReqDTO;
import com.anyujia.anyujialegalapi.feign.LegalAgentFeignClient;
import com.anyujia.anyujiachatservice.cache.RecentMessageCache;
import com.anyujia.anyujiachatservice.domain.entity.ChatConversation;
import com.anyujia.anyujiachatservice.domain.entity.ChatConversationMember;
import com.anyujia.anyujiachatservice.domain.entity.ChatMessage;
import com.anyujia.anyujiachatservice.mapper.ChatConversationMapper;
import com.anyujia.anyujiachatservice.mapper.ChatConversationMemberMapper;
import com.anyujia.anyujiachatservice.mapper.ChatMessageMapper;
import com.anyujia.anyujiachatservice.mapper.ChatUserConversationMapper;
import com.anyujia.anyujiachatservice.metrics.ChatMetrics;
import com.anyujia.anyujiachatservice.service.ConversationMemberService;
import com.anyujia.anyujiachatservice.service.MessageService;
import com.anyujia.anyujiachatservice.realtime.ChatMessagePublisher;
import com.anyujia.anyujiachatservice.service.moderation.ChatContentModerationService;
import com.anyujia.anyujiachatservice.service.ratelimit.ConversationRateLimiter;
import com.anyujia.anyujiachatservice.service.sequence.RedisConversationSequenceAllocator;
import com.anyujia.anyujiachatservice.service.unread.ChatUnreadService;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl implements MessageService {
    private final ChatMessageMapper messageMapper;
    private final ChatConversationMapper conversationMapper;
    private final ChatConversationMemberMapper memberMapper;
    private final ChatUserConversationMapper userConversationMapper;
    private final ConversationMemberService memberService;
    private final ChatMessagePublisher pushRouter;
    private final RecentMessageCache recentMessageCache;
    private final ChatContentModerationService contentModerationService;
    private final ConversationRateLimiter conversationRateLimiter;
    private final RedisConversationSequenceAllocator sequenceAllocator;
    private final ChatUnreadService unreadService;
    private final ChatMetrics chatMetrics;
    private final LegalAgentFeignClient legalAgentFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO send(Long userId, MessageSendReqDTO request) {
        long sendStart = System.nanoTime();
        try {
            validateType(request.getType());
            Long recipientId = memberService.requireCounterparty(request.getSessionId(), userId);
            boolean agentConversation = ChatConstants.AGENT_USER_ID == recipientId;
            ChatMessage existing = findByClientId(userId, request.getClientMessageId());
            if (existing != null) {
                return toVO(existing);
            }
            if (!conversationRateLimiter.tryAcquire(request.getSessionId())) {
                chatMetrics.incrementRateLimited();
                throw new ServiceException("Conversation message rate limit exceeded",
                        ResultCode.INVALID_PARA.getCode());
            }
            ChatMessage message = new ChatMessage();
            message.setClientMessageId(request.getClientMessageId());
            message.setConversationId(request.getSessionId());
            message.setConversationSeq(allocateConversationSequence(request.getSessionId()));
            message.setSenderId(userId);
            message.setRecipientId(recipientId);
            message.setType(request.getType());
            message.setContent(contentModerationService.moderate(request.getContent()));
            message.setStatus(ChatConstants.MESSAGE_UNREAD);
            message.setVisited(ChatConstants.MESSAGE_NOT_VISITED);
            message.setCreatedAt(LocalDateTime.now());
            long dbStart = System.nanoTime();
            try {
                try {
                    messageMapper.insert(message);
                } catch (DuplicateKeyException ex) {
                    return toVO(findRequiredByClientId(userId, request.getClientMessageId()));
                }
                conversationMapper.update(null, new LambdaUpdateWrapper<ChatConversation>()
                        .eq(ChatConversation::getId, request.getSessionId())
                        .set(ChatConversation::getLastMessageId, message.getId())
                        .set(ChatConversation::getLastMessageSeq, message.getConversationSeq())
                        .set(ChatConversation::getNextMessageSeq, message.getConversationSeq())
                        .set(ChatConversation::getLastMessageTime, toEpochMilli(message.getCreatedAt()))
                        .set(ChatConversation::getUpdatedAt, message.getCreatedAt()));
                if (!agentConversation) {
                    unreadService.increment(recipientId, request.getSessionId());
                }
                long messageTime = toEpochMilli(message.getCreatedAt());
                int updatedIndexes = userConversationMapper.touchMessage(userId, request.getSessionId(), message.getId(),
                        message.getConversationSeq(), messageTime);
                if (!agentConversation) {
                    updatedIndexes += userConversationMapper.touchMessage(recipientId, request.getSessionId(), message.getId(),
                            message.getConversationSeq(), messageTime);
                }
                if (updatedIndexes != (agentConversation ? 1 : 2)) {
                    throw new ServiceException("User conversation index is incomplete");
                }
            } finally {
                chatMetrics.recordDbWrite(dbStart);
            }
            MessageVO result = toVO(message);
            if (agentConversation) {
                dispatchAgentReplyAfterCommit(userId, request.getSessionId(), request.getContent());
            } else {
                publishAfterCommit(recipientId, result);
            }
            return result;
        } finally {
            chatMetrics.recordSend(sendStart);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO sendAgentMessage(Long userId, AgentMessageReqDTO request) {
        ChatConversation conversation = conversationMapper.selectById(request.getChatConversationId());
        if (conversation == null || !Integer.valueOf(ChatConstants.CONVERSATION_TYPE_AGENT)
                .equals(conversation.getConversationType())) {
            throw new ServiceException("Agent conversation does not exist", ResultCode.INVALID_PARA.getCode());
        }
        Long tenantId = conversation.getTenantUserId();
        ChatMessage message = new ChatMessage();
        message.setConversationId(request.getChatConversationId());
        message.setConversationSeq(allocateConversationSequence(request.getChatConversationId()));
        message.setSenderId(ChatConstants.AGENT_USER_ID);
        message.setRecipientId(tenantId);
        message.setType(0);
        message.setContent(bounded(request.getContent(), 1024));
        message.setStatus(ChatConstants.MESSAGE_UNREAD);
        message.setVisited(ChatConstants.MESSAGE_NOT_VISITED);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        conversationMapper.update(null, new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getId, request.getChatConversationId())
                .set(ChatConversation::getLastMessageId, message.getId())
                .set(ChatConversation::getLastMessageSeq, message.getConversationSeq())
                .set(ChatConversation::getNextMessageSeq, message.getConversationSeq())
                .set(ChatConversation::getLastMessageTime, toEpochMilli(message.getCreatedAt()))
                .set(ChatConversation::getUpdatedAt, message.getCreatedAt()));
        unreadService.increment(tenantId, request.getChatConversationId());
        userConversationMapper.touchMessage(tenantId, request.getChatConversationId(), message.getId(),
                message.getConversationSeq(), toEpochMilli(message.getCreatedAt()));
        MessageVO result = toVO(message);
        publishAfterCommit(tenantId, result);
        return result;
    }

    @Override
    public List<MessageVO> list(Long userId, MessageListReqDTO request) {
        memberService.requireMember(request.getSessionId(), userId);
        int count = request.getCount() == null ? 20 : Math.max(1, Math.min(request.getCount(), ChatConstants.MAX_PAGE_SIZE));
        Long sequenceCursor = parseCursor(request.getLastConversationSeq(), "conversation sequence");
        Long messageIdCursor = parseCursor(request.getLastMessageId(), "message ID");
        if (sequenceCursor == null && messageIdCursor == null) {
            return recentMessageCache.getRecent(request.getSessionId(), count,
                    () -> queryLatest(request.getSessionId(), ChatConstants.MAX_PAGE_SIZE));
        }
        LambdaQueryWrapper<ChatMessage> query = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, request.getSessionId());
        if (sequenceCursor != null) {
            query.lt(!Boolean.TRUE.equals(request.getNeedCurMessage()), ChatMessage::getConversationSeq, sequenceCursor)
                    .le(Boolean.TRUE.equals(request.getNeedCurMessage()), ChatMessage::getConversationSeq, sequenceCursor)
                    .orderByDesc(ChatMessage::getConversationSeq);
        } else {
            query.lt(messageIdCursor != null && !Boolean.TRUE.equals(request.getNeedCurMessage()),
                            ChatMessage::getId, messageIdCursor)
                    .le(messageIdCursor != null && Boolean.TRUE.equals(request.getNeedCurMessage()),
                            ChatMessage::getId, messageIdCursor)
                    .orderByDesc(ChatMessage::getId);
        }
        query.last("LIMIT " + count);
        List<MessageVO> result = new ArrayList<>(messageMapper.selectList(query).stream().map(this::toVO).toList());
        Collections.reverse(result);
        return result;
    }

    @Override
    public List<MessageVO> sync(Long userId, MessageSyncReqDTO request) {
        long start = System.nanoTime();
        try {
            memberService.requireMember(request.getSessionId(), userId);
            int count = request.getCount() == null
                    ? ChatConstants.MAX_PAGE_SIZE
                    : Math.max(1, Math.min(request.getCount(), ChatConstants.MAX_PAGE_SIZE));
            Long sequenceCursor = parseCursor(request.getAfterConversationSeq(), "conversation sequence");
            Long messageIdCursor = parseCursor(request.getAfterMessageId(), "message ID");
            if (sequenceCursor == null && messageIdCursor == null) {
                throw new ServiceException("A message sync cursor is required", ResultCode.INVALID_PARA.getCode());
            }
            LambdaQueryWrapper<ChatMessage> query = new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getConversationId, request.getSessionId());
            if (sequenceCursor != null) {
                query.gt(ChatMessage::getConversationSeq, sequenceCursor)
                        .orderByAsc(ChatMessage::getConversationSeq);
            } else {
                query.gt(ChatMessage::getId, messageIdCursor)
                        .orderByAsc(ChatMessage::getId);
            }
            query.last("LIMIT " + count);
            return messageMapper.selectList(query)
                    .stream()
                    .map(this::toVO)
                    .toList();
        } finally {
            chatMetrics.recordSync(start);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markVisited(Long userId, MessageVisitedReqDTO request) {
        ChatConversationMember member = memberService.requireMember(request.getSessionId(), userId);
        unreadService.reset(userId, request.getSessionId());
        ChatMessage latest = messageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, request.getSessionId())
                .orderByDesc(ChatMessage::getConversationSeq)
                .last("LIMIT 1"));
        memberMapper.update(null, new LambdaUpdateWrapper<ChatConversationMember>()
                .eq(ChatConversationMember::getId, member.getId())
                .set(ChatConversationMember::getUnreadCount, 0)
                .set(ChatConversationMember::getLastReadMessageId, latest == null ? null : latest.getId())
                .set(ChatConversationMember::getLastReadConversationSeq,
                        latest == null ? null : latest.getConversationSeq())
                .set(ChatConversationMember::getUpdatedAt, LocalDateTime.now()));
        if (userConversationMapper.markVisited(request.getSessionId(), userId,
                latest == null ? null : latest.getId(),
                latest == null ? null : latest.getConversationSeq()) != 1) {
            throw new ServiceException("User conversation index is incomplete");
        }
        messageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, request.getSessionId())
                .eq(ChatMessage::getRecipientId, userId)
                .eq(ChatMessage::getVisited, ChatConstants.MESSAGE_NOT_VISITED)
                .set(ChatMessage::getVisited, ChatConstants.MESSAGE_VISITED));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, MessageReadReqDTO request) {
        memberService.requireMember(request.getSessionId(), userId);
        List<Long> ids;
        try {
            ids = request.getMessageIds().stream().map(Long::valueOf).toList();
        } catch (NumberFormatException ex) {
            throw new ServiceException("Invalid message ID", ResultCode.INVALID_PARA.getCode());
        }
        messageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, request.getSessionId())
                .eq(ChatMessage::getRecipientId, userId)
                .in(ChatMessage::getId, ids)
                .set(ChatMessage::getStatus, ChatConstants.MESSAGE_READ));
    }

    private ChatMessage findByClientId(Long senderId, String clientMessageId) {
        return messageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSenderId, senderId)
                .eq(ChatMessage::getClientMessageId, clientMessageId));
    }

    private ChatMessage findRequiredByClientId(Long senderId, String clientMessageId) {
        ChatMessage message = findByClientId(senderId, clientMessageId);
        if (message == null) {
            throw new ServiceException("Message idempotency conflict");
        }
        return message;
    }

    private void validateType(Integer type) {
        if (type == null || type < 0 || type > 4) {
            throw new ServiceException("Unsupported message type", ResultCode.INVALID_PARA.getCode());
        }
    }

    private Long parseCursor(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long cursor = Long.parseLong(value);
            if (cursor < 0) {
                throw new NumberFormatException("negative cursor");
            }
            return cursor;
        } catch (NumberFormatException ex) {
            throw new ServiceException("Invalid " + fieldName, ResultCode.INVALID_PARA.getCode());
        }
    }

    private Long allocateConversationSequence(Long conversationId) {
        try {
            return sequenceAllocator.next(conversationId);
        } catch (RuntimeException ex) {
            log.warn("Redis sequence allocation failed, falling back to DB, conversationId={}", conversationId, ex);
            return allocateConversationSequenceFromDb(conversationId);
        }
    }

    private Long allocateConversationSequenceFromDb(Long conversationId) {
        Long current = conversationMapper.selectNextMessageSeqForUpdate(conversationId);
        if (current == null) {
            throw new ServiceException("Conversation does not exist", ResultCode.INVALID_PARA.getCode());
        }
        final long next;
        try {
            next = Math.addExact(current, 1L);
        } catch (ArithmeticException ex) {
            throw new ServiceException("Conversation sequence exhausted");
        }
        if (conversationMapper.updateNextMessageSeq(conversationId, next) != 1) {
            throw new ServiceException("Failed to allocate conversation sequence");
        }
        return next;
    }

    private MessageVO toVO(ChatMessage message) {
        MessageVO vo = new MessageVO();
        vo.setMessageId(String.valueOf(message.getId()));
        vo.setClientMessageId(message.getClientMessageId());
        vo.setConversationSeq(String.valueOf(message.getConversationSeq()));
        vo.setSessionId(message.getConversationId());
        vo.setFromId(message.getSenderId());
        vo.setContent(message.getContent());
        vo.setType(message.getType());
        vo.setStatus(message.getStatus());
        vo.setVisited(message.getVisited());
        vo.setCreateTime(toEpochMilli(message.getCreatedAt()));
        return vo;
    }

    private long toEpochMilli(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private List<MessageVO> queryLatest(Long conversationId, int count) {
        List<MessageVO> result = new ArrayList<>(messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getConversationSeq)
                .last("LIMIT " + count))
                .stream()
                .map(this::toVO)
                .toList());
        Collections.reverse(result);
        return result;
    }

    private void publishAfterCommit(Long recipientId, MessageVO message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            recentMessageCache.append(message);
            tryPush(recipientId, message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recentMessageCache.append(message);
                tryPush(recipientId, message);
            }
        });
    }

    private void tryPush(Long recipientId, MessageVO message) {
        try {
            pushRouter.route(recipientId, message);
        } catch (RuntimeException ex) {
            chatMetrics.incrementPushFailure();
            log.error("Chat message persisted but online push failed, messageId={}, recipientId={}",
                    message.getMessageId(), recipientId, ex);
        }
    }

    private void dispatchAgentReplyAfterCommit(Long userId, Long conversationId, String query) {
        Runnable dispatch = () -> {
            try {
                LegalAgentAnswerReqDTO request = new LegalAgentAnswerReqDTO();
                request.setChatConversationId(conversationId);
                request.setQuery(query);
                legalAgentFeignClient.answer(userId, ChatConstants.USER_TYPE_TENANT, "app", request);
            } catch (Exception ex) {
                log.error("Legal agent reply dispatch failed, conversationId={}", conversationId, ex);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            CompletableFuture.runAsync(dispatch);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(dispatch);
            }
        });
    }

    private String bounded(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.substring(0, Math.min(value.length(), maxLength));
    }
}
