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
import com.anyujia.anyujiachatservice.cache.ChatConversationIndexCache;
import com.anyujia.anyujiachatservice.domain.entity.ChatConversation;
import com.anyujia.anyujiachatservice.domain.entity.ChatConversationMember;
import com.anyujia.anyujiachatservice.domain.entity.ChatMessage;
import com.anyujia.anyujiachatservice.mapper.ChatConversationMapper;
import com.anyujia.anyujiachatservice.mapper.ChatConversationMemberMapper;
import com.anyujia.anyujiachatservice.mapper.ChatMessageMapper;
import com.anyujia.anyujiachatservice.mapper.ChatUserConversationMapper;
import com.anyujia.anyujiachatservice.metrics.ChatMetrics;
import com.anyujia.anyujiachatservice.realtime.ChatMessageOutboxService;
import com.anyujia.anyujiachatservice.service.ConversationMemberService;
import com.anyujia.anyujiachatservice.service.MessageService;
import com.anyujia.anyujiachatservice.realtime.ChatMessagePublisher;
import com.anyujia.anyujiachatservice.service.moderation.ChatContentModerationService;
import com.anyujia.anyujiachatservice.service.ratelimit.ConversationRateLimiter;
import com.anyujia.anyujiachatservice.service.sequence.RedisConversationSequenceAllocator;
import com.anyujia.anyujiachatservice.service.unread.ChatUnreadService;
import com.anyujia.anyujiachatservice.sharding.ChatMessageShardContext;
import com.anyujia.anyujiachatservice.sharding.ChatMessageShardRouter;
import com.anyujia.anyujiachatservice.sharding.ChatMessageShardStore;
import com.anyujia.anyujiachatservice.sharding.ChatMessageStorageMode;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
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
    private final ChatConversationIndexCache conversationIndexCache;
    private final ChatMessageShardRouter shardRouter;
    private final ChatMessageOutboxService outboxService;

    @org.springframework.beans.factory.annotation.Value("${chat.message-sharding.mode:LEGACY_ONLY}")
    private ChatMessageStorageMode storageMode = ChatMessageStorageMode.LEGACY_ONLY;

    @org.springframework.beans.factory.annotation.Value("${chat.message-sharding.read-fallback:true}")
    private boolean messageReadFallback;

    /**
     * Whether the send-path idempotency lookup falls back to the legacy table when the shard has no
     * matching {@code (sender_id, client_message_id)} row. The lookup misses on every new message,
     * so the fallback costs a full extra query per send. Once backfill/verification reports
     * {@code missing=0} every legacy row also exists in its shard, and the fallback can be turned
     * off (the gray rollout's final step).
     */
    @org.springframework.beans.factory.annotation.Value(
            "${chat.message-sharding.idempotency-fallback:${chat.message-sharding.read-fallback:true}}")
    private boolean messageIdempotencyFallback = true;

    @org.springframework.beans.factory.annotation.Value("${chat.message-sharding.physical-enabled:false}")
    private boolean physicalEnabled;

    @Autowired(required = false)
    private ChatMessageShardStore shardStore;

    /** Compatibility constructor for service-level tests and non-Spring callers. */
    public MessageServiceImpl(ChatMessageMapper messageMapper,
                              ChatConversationMapper conversationMapper,
                              ChatConversationMemberMapper memberMapper,
                              ChatUserConversationMapper userConversationMapper,
                              ConversationMemberService memberService,
                              ChatMessagePublisher pushRouter,
                              RecentMessageCache recentMessageCache,
                              ChatContentModerationService contentModerationService,
                              ConversationRateLimiter conversationRateLimiter,
                              RedisConversationSequenceAllocator sequenceAllocator,
                              ChatUnreadService unreadService,
                              ChatMetrics chatMetrics,
                              LegalAgentFeignClient legalAgentFeignClient,
                              ChatConversationIndexCache conversationIndexCache) {
        this(messageMapper, conversationMapper, memberMapper, userConversationMapper, memberService,
                pushRouter, recentMessageCache, contentModerationService, conversationRateLimiter,
                sequenceAllocator, unreadService, chatMetrics, legalAgentFeignClient,
                conversationIndexCache, new ChatMessageShardRouter(32), null);
        this.storageMode = ChatMessageStorageMode.LEGACY_ONLY;
    }

    @Override
    public MessageVO send(Long userId, MessageSendReqDTO request) {
        selectMessageShard(request.getSessionId());
        long sendStart = System.nanoTime();
        try {
            validateType(request.getType());
            long counterpartyStart = System.nanoTime();
            Long recipientId = memberService.requireCounterparty(request.getSessionId(), userId);
            chatMetrics.recordRequireCounterparty(counterpartyStart);
            boolean agentConversation = ChatConstants.AGENT_USER_ID == recipientId;
            long findClientStart = System.nanoTime();
            ChatMessage existing = findByClientId(userId, request.getClientMessageId(), request.getSessionId());
            chatMetrics.recordFindClient(findClientStart);
            if (existing != null) {
                return toVO(existing);
            }
            long rateLimitStart = System.nanoTime();
            if (!conversationRateLimiter.tryAcquire(request.getSessionId())) {
                chatMetrics.recordRateLimit(rateLimitStart);
                chatMetrics.incrementRateLimited();
                throw new ServiceException("Conversation message rate limit exceeded",
                        ResultCode.INVALID_PARA.getCode());
            }
            chatMetrics.recordRateLimit(rateLimitStart);
            ChatMessage message = new ChatMessage();
            message.setClientMessageId(request.getClientMessageId());
            message.setConversationId(request.getSessionId());
            long sequenceStart = System.nanoTime();
            message.setConversationSeq(allocateConversationSequence(request.getSessionId()));
            chatMetrics.recordSequence(sequenceStart);
            message.setSenderId(userId);
            message.setRecipientId(recipientId);
            message.setType(request.getType());
            message.setContent(contentModerationService.moderate(request.getContent()));
            message.setStatus(ChatConstants.MESSAGE_UNREAD);
            message.setVisited(ChatConstants.MESSAGE_NOT_VISITED);
            message.setCreatedAt(LocalDateTime.now());
            long dbStart = System.nanoTime();
            MessageVO result;
            try {
                try {
                    insertMessage(message);
                } catch (DuplicateKeyException ex) {
                    return toVO(findRequiredByClientId(userId, request.getClientMessageId(), request.getSessionId()));
                }
                advanceLatestMessageAfterCommit(message);
                result = toVO(message);
                List<Long> affectedUsers = agentConversation
                        ? List.of(userId)
                        : List.of(userId, recipientId);
                if (outboxService != null) {
                    enqueueOutboxBestEffort(result, recipientId, affectedUsers, agentConversation);
                }
            } finally {
                chatMetrics.recordDbWrite(dbStart);
            }
            if (agentConversation) {
                dispatchAgentReplyAfterCommit(userId, request.getSessionId(), request.getContent());
            } else if (outboxService == null) {
                publishPersistedMessage(recipientId, result);
            }
            return result;
        } finally {
            ChatMessageShardContext.clear();
            chatMetrics.recordSend(sendStart);
        }
    }

    @Override
    public MessageVO sendAgentMessage(Long userId, AgentMessageReqDTO request) {
        selectMessageShard(request.getChatConversationId());
        try {
        ChatConversation conversation = conversationMapper.selectById(request.getChatConversationId());
        if (conversation == null || !Integer.valueOf(ChatConstants.CONVERSATION_TYPE_AGENT)
                .equals(conversation.getConversationType())) {
            throw new ServiceException("Agent conversation does not exist", ResultCode.INVALID_PARA.getCode());
        }
        Long tenantId = conversation.getTenantUserId();
        if (!tenantId.equals(userId)) {
            throw new ServiceException("Agent conversation tenant mismatch", ResultCode.INVALID_PARA.getCode());
        }
        String deliveryKey = request.getRequestId() == null || request.getRequestId().isBlank()
                ? java.util.UUID.randomUUID().toString() : request.getRequestId();
        ChatMessage existing = findByClientId(ChatConstants.AGENT_USER_ID, deliveryKey,
                request.getChatConversationId());
        if (existing != null) {
            return toVO(existing);
        }
        selectMessageShard(request.getChatConversationId());
        ChatMessage message = new ChatMessage();
        message.setClientMessageId(deliveryKey);
        message.setConversationId(request.getChatConversationId());
        message.setConversationSeq(allocateConversationSequence(request.getChatConversationId()));
        message.setSenderId(ChatConstants.AGENT_USER_ID);
        message.setRecipientId(tenantId);
        message.setType(0);
        message.setContent(bounded(request.getContent(), 1024));
        message.setStatus(ChatConstants.MESSAGE_UNREAD);
        message.setVisited(ChatConstants.MESSAGE_NOT_VISITED);
        message.setCreatedAt(LocalDateTime.now());
        try {
            insertMessage(message);
        } catch (DuplicateKeyException ex) {
            ChatMessage duplicate = findByClientId(ChatConstants.AGENT_USER_ID, deliveryKey,
                    request.getChatConversationId());
            if (duplicate != null) {
                return toVO(duplicate);
            }
            throw ex;
        }
        advanceLatestMessageAfterCommit(message);
        MessageVO result = toVO(message);
        if (outboxService != null) {
            enqueueOutboxBestEffort(result, tenantId, List.of(tenantId), false);
        } else {
            publishPersistedMessage(tenantId, result);
        }
        return result;
        } finally {
            ChatMessageShardContext.clear();
        }
    }

    @Override
    public List<MessageVO> list(Long userId, MessageListReqDTO request) {
        selectMessageShard(request.getSessionId());
        try {
        memberService.requireMember(request.getSessionId(), userId);
        int count = request.getCount() == null ? 20 : Math.max(1, Math.min(request.getCount(), ChatConstants.MAX_PAGE_SIZE));
        Long sequenceCursor = parseCursor(request.getLastConversationSeq(), "conversation sequence");
        Long messageIdCursor = parseCursor(request.getLastMessageId(), "message ID");
        if (sequenceCursor == null && messageIdCursor == null) {
            return recentMessageCache.getRecent(request.getSessionId(), count,
                    () -> queryLatest(request.getSessionId(), ChatConstants.MAX_PAGE_SIZE));
        }
        if (physicalMessageRouting()) {
            return listFromShard(request.getSessionId(), sequenceCursor, messageIdCursor,
                    Boolean.TRUE.equals(request.getNeedCurMessage()), count);
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
        } catch (RuntimeException ex) {
            if (canFallbackToLegacy()) {
                ChatMessageShardContext.clear();
                return listFromLegacy(userId, request);
            }
            throw ex;
        } finally {
            ChatMessageShardContext.clear();
        }
    }

    @Override
    public List<MessageVO> sync(Long userId, MessageSyncReqDTO request) {
        selectMessageShard(request.getSessionId());
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
            if (physicalMessageRouting()) {
                return syncFromShard(request.getSessionId(), sequenceCursor, messageIdCursor, count);
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
        } catch (RuntimeException ex) {
            if (canFallbackToLegacy()) {
                ChatMessageShardContext.clear();
                return syncFromLegacy(userId, request);
            }
            throw ex;
        } finally {
            ChatMessageShardContext.clear();
            chatMetrics.recordSync(start);
        }
    }

    @Override
    public void markVisited(Long userId, MessageVisitedReqDTO request) {
        selectMessageShard(request.getSessionId());
        try {
        ChatConversationMember member = memberService.requireMember(request.getSessionId(), userId);
        unreadService.reset(userId, request.getSessionId());
        ChatMessage latest = latestMessage(request.getSessionId());
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
        if (physicalMessageRouting()) {
            shardStore.markVisited(request.getSessionId(), userId,
                    ChatConstants.MESSAGE_NOT_VISITED, ChatConstants.MESSAGE_VISITED);
        } else {
            messageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                    .eq(ChatMessage::getConversationId, request.getSessionId())
                    .eq(ChatMessage::getRecipientId, userId)
                    .eq(ChatMessage::getVisited, ChatConstants.MESSAGE_NOT_VISITED)
                    .set(ChatMessage::getVisited, ChatConstants.MESSAGE_VISITED));
        }
        } finally {
            ChatMessageShardContext.clear();
        }
    }

    @Override
    public void markRead(Long userId, MessageReadReqDTO request) {
        selectMessageShard(request.getSessionId());
        try {
        memberService.requireMember(request.getSessionId(), userId);
        List<Long> ids;
        try {
            ids = request.getMessageIds().stream().map(Long::valueOf).toList();
        } catch (NumberFormatException ex) {
            throw new ServiceException("Invalid message ID", ResultCode.INVALID_PARA.getCode());
        }
        if (physicalMessageRouting()) {
            shardStore.markRead(request.getSessionId(), userId, ids, ChatConstants.MESSAGE_READ);
        } else {
            messageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                    .eq(ChatMessage::getConversationId, request.getSessionId())
                    .eq(ChatMessage::getRecipientId, userId)
                    .in(ChatMessage::getId, ids)
                    .set(ChatMessage::getStatus, ChatConstants.MESSAGE_READ));
        }
        } finally {
            ChatMessageShardContext.clear();
        }
    }

    private void selectMessageShard(Long conversationId) {
        if (storageMode != ChatMessageStorageMode.LEGACY_ONLY) {
            ChatMessageShardContext.use(shardRouter.tableName(conversationId));
        }
    }

    private boolean physicalMessageRouting() {
        return physicalEnabled && storageMode != ChatMessageStorageMode.LEGACY_ONLY && shardStore != null;
    }

    private void insertShardPhysical(ChatMessage message) {
        try {
            shardStore.insert(message);
        } catch (DuplicateKeyException ex) {
            ChatMessage existing = shardStore.findByClientId(message.getConversationId(),
                    message.getSenderId(), message.getClientMessageId());
            if (existing == null || !message.getId().equals(existing.getId())) {
                throw ex;
            }
        }
    }

    private ChatMessage latestMessage(Long conversationId) {
        if (physicalMessageRouting()) {
            return shardStore.selectLatest(conversationId);
        }
        return messageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getConversationSeq)
                .last("LIMIT 1"));
    }

    private List<MessageVO> listFromShard(Long conversationId, Long sequenceCursor, Long messageIdCursor,
                                          boolean needCur, int count) {
        List<ChatMessage> messages = sequenceCursor != null
                ? shardStore.listBySeq(conversationId, sequenceCursor, needCur, count)
                : shardStore.listById(conversationId, messageIdCursor, needCur, count);
        List<MessageVO> result = new ArrayList<>(messages.stream().map(this::toVO).toList());
        Collections.reverse(result);
        return result;
    }

    private List<MessageVO> syncFromShard(Long conversationId, Long sequenceCursor, Long messageIdCursor,
                                          int count) {
        List<ChatMessage> messages = sequenceCursor != null
                ? shardStore.syncBySeq(conversationId, sequenceCursor, count)
                : shardStore.syncById(conversationId, messageIdCursor, count);
        return messages.stream().map(this::toVO).toList();
    }

    private boolean canFallbackToLegacy() {
        return storageMode != ChatMessageStorageMode.LEGACY_ONLY && messageReadFallback
                && ChatMessageShardContext.currentTable() != null;
    }

    private List<MessageVO> listFromLegacy(Long userId, MessageListReqDTO request) {
        memberService.requireMember(request.getSessionId(), userId);
        int count = request.getCount() == null ? 20
                : Math.max(1, Math.min(request.getCount(), ChatConstants.MAX_PAGE_SIZE));
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
        List<MessageVO> result = new ArrayList<>(messageMapper.selectList(query).stream()
                .map(this::toVO).toList());
        Collections.reverse(result);
        return result;
    }

    private List<MessageVO> syncFromLegacy(Long userId, MessageSyncReqDTO request) {
        memberService.requireMember(request.getSessionId(), userId);
        int count = request.getCount() == null ? ChatConstants.MAX_PAGE_SIZE
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
        return messageMapper.selectList(query).stream().map(this::toVO).toList();
    }

    private ChatMessage findByClientId(Long senderId, String clientMessageId) {
        return findByClientId(senderId, clientMessageId, null);
    }

    private ChatMessage findByClientId(Long senderId, String clientMessageId, Long conversationId) {
        if (physicalMessageRouting() && conversationId != null) {
            ChatMessage shardMessage = shardStore.findByClientId(conversationId, senderId, clientMessageId);
            if (shardMessage != null || !messageIdempotencyFallback) {
                return shardMessage;
            }
            return findByClientIdInTable(senderId, clientMessageId, null);
        }
        if (conversationId == null || storageMode != ChatMessageStorageMode.SHARD_ONLY) {
            ChatMessageShardContext.clear();
            return messageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getSenderId, senderId)
                    .eq(ChatMessage::getClientMessageId, clientMessageId));
        }
        ChatMessage shardMessage = findByClientIdInTable(senderId, clientMessageId,
                shardRouter.tableName(conversationId));
        if (shardMessage != null || !messageIdempotencyFallback) {
            return shardMessage;
        }
        return findByClientIdInTable(senderId, clientMessageId, null);
    }

    private ChatMessage findByClientIdInTable(Long senderId, String clientMessageId, String tableName) {
        if (tableName == null) {
            ChatMessageShardContext.clear();
        } else {
            ChatMessageShardContext.use(tableName);
        }
        try {
            return messageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getSenderId, senderId)
                    .eq(ChatMessage::getClientMessageId, clientMessageId));
        } finally {
            ChatMessageShardContext.clear();
        }
    }

    private void insertMessage(ChatMessage message) {
        if (physicalMessageRouting()) {
            if (storageMode == ChatMessageStorageMode.DUAL_WRITE) {
                // Legacy first so the mapper assigns the snowflake id, then reuse it for the shard.
                ChatMessageShardContext.clear();
                messageMapper.insert(message);
            }
            insertShardPhysical(message);
            return;
        }
        if (storageMode == ChatMessageStorageMode.LEGACY_ONLY) {
            ChatMessageShardContext.clear();
            messageMapper.insert(message);
            return;
        }
        if (storageMode == ChatMessageStorageMode.SHARD_ONLY) {
            // The idempotency check (findByClientId) clears the shard context, so set it
            // again here; otherwise a sharding-only write would fall through to the legacy table.
            ChatMessageShardContext.use(shardRouter.tableName(message.getConversationId()));
            try {
                messageMapper.insert(message);
            } finally {
                ChatMessageShardContext.clear();
            }
            return;
        }
        ChatMessageShardContext.clear();
        messageMapper.insert(message);
        try {
            ChatMessageShardContext.use(shardRouter.tableName(message.getConversationId()));
            try {
                messageMapper.insert(message);
            } catch (DuplicateKeyException ex) {
                ChatMessage existing = findByClientIdInTable(message.getSenderId(),
                        message.getClientMessageId(), ChatMessageShardContext.currentTable());
                if (existing == null || !message.getId().equals(existing.getId())) {
                    throw ex;
                }
            }
        } finally {
            ChatMessageShardContext.clear();
        }
    }

    private ChatMessage findRequiredByClientId(Long senderId, String clientMessageId, Long conversationId) {
        ChatMessage message = findByClientId(senderId, clientMessageId, conversationId);
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

    private void advanceLatestMessage(ChatMessage message) {
        conversationMapper.advanceLatestMessage(message.getConversationId(), message.getId(),
                message.getConversationSeq(), toEpochMilli(message.getCreatedAt()), message.getCreatedAt());
    }

    private void advanceLatestMessageAfterCommit(ChatMessage message) {
        // Conversation metadata is updated out-of-band (eventual consistency): there is no
        // distributed transaction tying it to the message write, and keeping the hot-row UPDATE on
        // the send path would serialize every write to the same conversation. The UPDATE is
        // idempotent (GREATEST/CASE guards monotonicity), so a late or lost update self-heals.
        CompletableFuture.runAsync(() -> {
            try {
                advanceLatestMessage(message);
            } catch (RuntimeException ex) {
                log.error("Conversation metadata update failed, conversationId={}, messageId={}",
                        message.getConversationId(), message.getId(), ex);
            }
        });
    }

    /**
     * Best-effort outbox enqueue after the message is already durable in its shard. Without a
     * distributed transaction the outbox write is not atomic with the message write, so a transient
     * outbox failure must not fail the send: the client would retry and hit the idempotency check
     * without re-enqueuing. The shard is the source of truth; push/unread/index side effects are
     * eventual and reconciled by the shard verification/backfill jobs.
     *
     * <p>The enqueue itself is handed to the outbox service's bounded executor. It used to run
     * inline on the send thread, which put a second main-database write (~65 ms under load) on the
     * critical path behind the shard INSERT; the caller already holds a durable message, so the
     * client response does not need to wait for the outbox row.
     */
    private void enqueueOutboxBestEffort(MessageVO message, Long recipientId,
                                         List<Long> affectedUsers, boolean agentConversation) {
        try {
            if (agentConversation) {
                outboxService.enqueueDerivedEventsAsync(affectedUsers, false, null, message);
            } else {
                outboxService.enqueueMessageEventsAsync(recipientId, affectedUsers, true, message);
            }
        } catch (RuntimeException ex) {
            log.error("Outbox enqueue failed after message persisted to shard, sessionId={}, messageId={}",
                    message.getSessionId(), message.getMessageId(), ex);
        }
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
        List<ChatMessage> messages;
        if (physicalMessageRouting()) {
            messages = shardStore.listLatest(conversationId, count);
        } else {
            messages = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getConversationId, conversationId)
                    .orderByDesc(ChatMessage::getConversationSeq)
                    .last("LIMIT " + count));
        }
        List<MessageVO> result = new ArrayList<>(messages.stream().map(this::toVO).toList());
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

    private void publishPersistedMessage(Long recipientId, MessageVO message) {
        if (outboxService == null) {
            publishAfterCommit(recipientId, message);
            return;
        }
        outboxService.enqueue(recipientId, message);
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
