package com.anyujia.anyujiachatservice.service.impl;

import com.anyujia.anyujiaadminapi.app.user.domain.vo.AppUserVO;
import com.anyujia.anyujiachatapi.constants.ChatConstants;
import com.anyujia.anyujiachatapi.domain.dto.ConversationCreateReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.ConversationContextReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.ConversationGetReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.ConversationHouseReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.ConversationListReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.ConversationCreateVO;
import com.anyujia.anyujiachatapi.domain.vo.ConversationContextVO;
import com.anyujia.anyujiachatapi.domain.vo.ConversationVO;
import com.anyujia.anyujiachatservice.domain.entity.ChatConversation;
import com.anyujia.anyujiachatservice.domain.entity.ChatConversationMember;
import com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation;
import com.anyujia.anyujiachatservice.mapper.ChatConversationMapper;
import com.anyujia.anyujiachatservice.mapper.ChatConversationMemberMapper;
import com.anyujia.anyujiachatservice.mapper.ChatUserConversationMapper;
import com.anyujia.anyujiachatservice.service.ConversationMemberService;
import com.anyujia.anyujiachatservice.service.ConversationService;
import com.anyujia.anyujiachatservice.service.identity.ChatParticipantIdentityValidator;
import com.anyujia.anyujiachatservice.service.house.ChatHousePublishProvider;
import com.anyujia.anyujiachatservice.service.profile.ChatUserProfileProvider;
import com.anyujia.anyujiahouseapi.domain.vo.HousePublishConsultVO;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {
    private final ChatConversationMapper conversationMapper;
    private final ChatConversationMemberMapper memberMapper;
    private final ChatUserConversationMapper userConversationMapper;
    private final ConversationMemberService memberService;
    private final ChatUserProfileProvider profileService;
    private final ChatParticipantIdentityValidator identityValidator;
    private final ChatHousePublishProvider housePublishProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationCreateVO create(Long userId, Integer userType, ConversationCreateReqDTO request) {
        requireCUser(userId);
        HousePublishConsultVO publication = housePublishProvider.getRequired(request.getHousePublishId());
        long tenantId;
        long landlordId;
        if (Integer.valueOf(ChatConstants.USER_TYPE_TENANT).equals(userType)) {
            tenantId = userId;
            landlordId = publication.getLandlordAppUserId();
            if (request.getCounterpartyUserId() != null
                    && !request.getCounterpartyUserId().equals(landlordId)) {
                throw invalid("The requested landlord does not own this publication");
            }
        } else if (Integer.valueOf(ChatConstants.USER_TYPE_LANDLORD).equals(userType)) {
            if (!userId.equals(publication.getLandlordAppUserId())) {
                throw invalid("The landlord does not own this publication");
            }
            if (request.getCounterpartyUserId() == null) {
                throw invalid("Tenant user ID is required when a landlord creates a conversation");
            }
            tenantId = request.getCounterpartyUserId();
            landlordId = userId;
        } else {
            throw invalid("Only tenant and landlord identities can create conversations");
        }
        if (tenantId == landlordId) {
            throw invalid("A user cannot create a conversation with themselves");
        }

        Map<Long, AppUserVO> users = profileService.getRequiredUsers(Set.of(tenantId, landlordId));
        identityValidator.validate(tenantId, landlordId);
        ChatConversation conversation = find(tenantId, landlordId, request.getHousePublishId());
        if (conversation == null) {
            conversation = new ChatConversation();
            conversation.setId(IdWorker.getId());
            conversation.setTenantUserId(tenantId);
            conversation.setLandlordUserId(landlordId);
            conversation.setHouseId(publication.getHouseId());
            conversation.setHousePublishId(request.getHousePublishId());
            conversation.setStatus(ChatConstants.CONVERSATION_ACTIVE);
            conversation.setCreatedAt(LocalDateTime.now());
            conversation.setUpdatedAt(conversation.getCreatedAt());
            if (conversationMapper.insertIgnore(conversation) == 1) {
                insertMember(conversation.getId(), tenantId, ChatConstants.USER_TYPE_TENANT);
                insertMember(conversation.getId(), landlordId, ChatConstants.USER_TYPE_LANDLORD);
                insertUserConversation(conversation, tenantId, ChatConstants.USER_TYPE_TENANT, landlordId);
                insertUserConversation(conversation, landlordId, ChatConstants.USER_TYPE_LANDLORD, tenantId);
            } else {
                conversation = find(tenantId, landlordId, request.getHousePublishId());
            }
        }
        if (conversation == null) {
            throw new ServiceException("Failed to create conversation");
        }
        return toCreateVO(conversation, userId, userType, users);
    }

    @Override
    public ConversationCreateVO get(Long userId, Integer userType, ConversationGetReqDTO request) {
        requireCUser(userId);
        if (userId.equals(request.getCounterpartyUserId())) {
            throw invalid("A user cannot query a conversation with themselves");
        }
        long tenantId;
        long landlordId;
        if (Integer.valueOf(ChatConstants.USER_TYPE_TENANT).equals(userType)) {
            tenantId = userId;
            landlordId = request.getCounterpartyUserId();
        } else if (Integer.valueOf(ChatConstants.USER_TYPE_LANDLORD).equals(userType)) {
            tenantId = request.getCounterpartyUserId();
            landlordId = userId;
        } else {
            throw invalid("Only tenant and landlord identities can query conversations");
        }
        ChatConversation conversation = find(tenantId, landlordId, request.getHousePublishId());
        if (conversation == null) {
            return null;
        }
        Map<Long, AppUserVO> users = profileService.getUsers(Set.of(tenantId, landlordId));
        return toCreateVO(conversation, userId, userType, users);
    }

    @Override
    public List<ConversationVO> list(Long userId, ConversationListReqDTO request) {
        requireCUser(userId);
        int count = request.getCount() == null ? 20 : Math.max(1, Math.min(request.getCount(), ChatConstants.MAX_PAGE_SIZE));
        Long cursorConversationId = parseCursor(request.getLastSessionId(), "conversation ID");
        if ((request.getLastSessionTime() == null) != (cursorConversationId == null)) {
            throw invalid("Both lastSessionTime and lastSessionId are required for conversation cursor pagination");
        }
        List<ChatUserConversation> conversations = userConversationMapper.selectRecentByUser(
                userId, request.getLastSessionTime(), cursorConversationId, count);
        Set<Long> otherIds = conversations.stream().map(ChatUserConversation::getOtherUserId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, AppUserVO> users = profileService.getUsers(otherIds);
        return conversations.stream().map(item -> toVO(item, users)).toList();
    }

    @Override
    public ConversationContextVO context(ConversationContextReqDTO request) {
        ChatConversation conversation = conversationMapper.selectById(request.getSessionId());
        if (conversation == null) {
            throw invalid("Conversation does not exist");
        }
        ConversationContextVO result = new ConversationContextVO();
        result.setSessionId(conversation.getId());
        result.setTenantUserId(conversation.getTenantUserId());
        result.setLandlordUserId(conversation.getLandlordUserId());
        result.setHouseId(conversation.getHouseId());
        result.setHousePublishId(conversation.getHousePublishId());
        result.setStatus(conversation.getStatus());
        return result;
    }

    @Override
    public boolean hasHouse(Long userId, ConversationHouseReqDTO request) {
        memberService.requireMember(request.getSessionId(), userId);
        ChatConversation conversation = conversationMapper.selectById(request.getSessionId());
        return conversation != null && request.getHousePublishId().equals(conversation.getHousePublishId());
    }

    private ChatConversation find(long tenantId, long landlordId, long housePublishId) {
        return conversationMapper.selectOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getTenantUserId, tenantId)
                .eq(ChatConversation::getLandlordUserId, landlordId)
                .eq(ChatConversation::getHousePublishId, housePublishId));
    }

    private void insertMember(Long conversationId, Long userId, int roleType) {
        ChatConversationMember member = new ChatConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRoleType(roleType);
        member.setUnreadCount(0);
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(member.getCreatedAt());
        memberMapper.insert(member);
    }

    private void insertUserConversation(ChatConversation conversation, Long userId,
                                        int roleType, Long otherUserId) {
        ChatUserConversation index = new ChatUserConversation();
        index.setUserId(userId);
        index.setConversationId(conversation.getId());
        index.setRoleType(roleType);
        index.setOtherUserId(otherUserId);
        index.setHouseId(conversation.getHouseId());
        index.setHousePublishId(conversation.getHousePublishId());
        index.setUnreadCount(0);
        index.setStatus(conversation.getStatus());
        index.setCreatedAt(conversation.getCreatedAt());
        index.setUpdatedAt(conversation.getUpdatedAt());
        userConversationMapper.insertIgnore(index);
    }

    private ConversationCreateVO toCreateVO(ChatConversation item, Long loginId, Integer loginType,
                                             Map<Long, AppUserVO> users) {
        Long otherId = otherUserId(item, loginId);
        int otherType = Integer.valueOf(ChatConstants.USER_TYPE_TENANT).equals(loginType)
                ? ChatConstants.USER_TYPE_LANDLORD : ChatConstants.USER_TYPE_TENANT;
        ConversationCreateVO vo = new ConversationCreateVO();
        vo.setSessionId(item.getId());
        vo.setHouseId(item.getHouseId());
        vo.setHousePublishId(item.getHousePublishId());
        vo.setLoginUser(profileService.toChatUser(users.get(loginId), loginId, loginType));
        vo.setOtherUser(profileService.toChatUser(users.get(otherId), otherId, otherType));
        return vo;
    }

    private ConversationVO toVO(ChatUserConversation item, Map<Long, AppUserVO> users) {
        ConversationVO vo = new ConversationVO();
        vo.setSessionId(item.getConversationId());
        vo.setHouseId(item.getHouseId());
        vo.setHousePublishId(item.getHousePublishId());
        vo.setLastMessageId(item.getLastMessageId());
        vo.setLastMessageSeq(item.getLastMessageSeq() == null ? null : String.valueOf(item.getLastMessageSeq()));
        vo.setLastSessionTime(item.getLastMessageTime());
        vo.setNotVisitedCount(item.getUnreadCount());
        int otherType = Integer.valueOf(ChatConstants.USER_TYPE_TENANT).equals(item.getRoleType())
                ? ChatConstants.USER_TYPE_LANDLORD : ChatConstants.USER_TYPE_TENANT;
        vo.setOtherUser(profileService.toChatUser(
                users.get(item.getOtherUserId()), item.getOtherUserId(), otherType));
        return vo;
    }

    private Long otherUserId(ChatConversation item, Long loginId) {
        return item.getTenantUserId().equals(loginId) ? item.getLandlordUserId() : item.getTenantUserId();
    }

    private void requireCUser(Long userId) {
        if (userId == null) {
            throw new ServiceException(ResultCode.TOKEN_EMPTY);
        }
    }

    private ServiceException invalid(String message) {
        return new ServiceException(message, ResultCode.INVALID_PARA.getCode());
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
            throw invalid("Invalid " + fieldName);
        }
    }
}
