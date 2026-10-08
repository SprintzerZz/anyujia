package com.anyujia.anyujiachatservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anyujia.anyujiaadminapi.app.user.domain.vo.AppUserVO;
import com.anyujia.anyujiachatapi.constants.ChatConstants;
import com.anyujia.anyujiachatapi.domain.dto.ConversationCreateReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.ConversationListReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.ConversationCreateVO;
import com.anyujia.anyujiachatapi.domain.vo.ChatUserVO;
import com.anyujia.anyujiachatservice.domain.entity.ChatConversation;
import com.anyujia.anyujiachatservice.domain.entity.ChatConversationMember;
import com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation;
import com.anyujia.anyujiachatservice.mapper.ChatConversationMapper;
import com.anyujia.anyujiachatservice.mapper.ChatConversationMemberMapper;
import com.anyujia.anyujiachatservice.mapper.ChatUserConversationMapper;
import com.anyujia.anyujiachatservice.service.impl.ConversationServiceImpl;
import com.anyujia.anyujiachatservice.service.identity.ChatParticipantIdentityValidator;
import com.anyujia.anyujiachatservice.service.house.ChatHousePublishProvider;
import com.anyujia.anyujiachatservice.service.profile.ChatUserProfileProvider;
import com.anyujia.anyujiahouseapi.domain.vo.HousePublishConsultVO;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConversationServiceImplTest {
    @Test
    void tenantCreatesConversationScopedToLandlordAndListing() {
        ChatConversationMapper conversationMapper = mock(ChatConversationMapper.class);
        ChatConversationMemberMapper memberMapper = mock(ChatConversationMemberMapper.class);
        ChatUserConversationMapper userConversationMapper = mock(ChatUserConversationMapper.class);
        ConversationMemberService memberService = mock(ConversationMemberService.class);
        ChatUserProfileProvider profileService = mock(ChatUserProfileProvider.class);
        ChatParticipantIdentityValidator identityValidator = mock(ChatParticipantIdentityValidator.class);
        ChatHousePublishProvider housePublishProvider = mock(ChatHousePublishProvider.class);
        ConversationService service = new ConversationServiceImpl(
                conversationMapper, memberMapper, userConversationMapper, memberService,
                profileService, identityValidator, housePublishProvider);

        AppUserVO tenant = user(10L, "tenant");
        AppUserVO landlord = user(20L, "landlord");
        when(profileService.getRequiredUsers(any())).thenReturn(Map.of(10L, tenant, 20L, landlord));
        when(profileService.toChatUser(any(), any(), any())).thenReturn(new ChatUserVO());
        HousePublishConsultVO publication = new HousePublishConsultVO();
        publication.setHousePublishId(400L);
        publication.setHouseId(300L);
        publication.setLandlordAppUserId(20L);
        when(housePublishProvider.getRequired(400L)).thenReturn(publication);
        when(conversationMapper.insertIgnore(any())).thenAnswer(invocation -> {
            ChatConversation conversation = invocation.getArgument(0);
            conversation.setId(99L);
            return 1;
        });

        ConversationCreateReqDTO request = new ConversationCreateReqDTO();
        request.setHousePublishId(400L);
        ConversationCreateVO result = service.create(10L, ChatConstants.USER_TYPE_TENANT, request);

        ArgumentCaptor<ChatConversation> conversation = ArgumentCaptor.forClass(ChatConversation.class);
        verify(conversationMapper).insertIgnore(conversation.capture());
        assertThat(conversation.getValue().getTenantUserId()).isEqualTo(10L);
        assertThat(conversation.getValue().getLandlordUserId()).isEqualTo(20L);
        assertThat(conversation.getValue().getHousePublishId()).isEqualTo(400L);
        assertThat(conversation.getValue().getHouseId()).isEqualTo(300L);
        assertThat(result.getSessionId()).isEqualTo(99L);
        verify(identityValidator).validate(10L, 20L);

        ArgumentCaptor<ChatConversationMember> members = ArgumentCaptor.forClass(ChatConversationMember.class);
        verify(memberMapper, org.mockito.Mockito.times(2)).insert(members.capture());
        assertThat(members.getAllValues()).extracting(ChatConversationMember::getRoleType)
                .containsExactly(ChatConstants.USER_TYPE_TENANT, ChatConstants.USER_TYPE_LANDLORD);

        ArgumentCaptor<ChatUserConversation> indexes = ArgumentCaptor.forClass(ChatUserConversation.class);
        verify(userConversationMapper, org.mockito.Mockito.times(2)).insertIgnore(indexes.capture());
        assertThat(indexes.getAllValues()).extracting(ChatUserConversation::getUserId)
                .containsExactly(10L, 20L);
        assertThat(indexes.getAllValues()).extracting(ChatUserConversation::getOtherUserId)
                .containsExactly(20L, 10L);
    }

    @Test
    void listUsesStableConversationCursor() {
        ChatConversationMapper conversationMapper = mock(ChatConversationMapper.class);
        ChatConversationMemberMapper memberMapper = mock(ChatConversationMemberMapper.class);
        ChatUserConversationMapper userConversationMapper = mock(ChatUserConversationMapper.class);
        ConversationMemberService memberService = mock(ConversationMemberService.class);
        ChatUserProfileProvider profileService = mock(ChatUserProfileProvider.class);
        ChatParticipantIdentityValidator identityValidator = mock(ChatParticipantIdentityValidator.class);
        ChatHousePublishProvider housePublishProvider = mock(ChatHousePublishProvider.class);
        ConversationService service = new ConversationServiceImpl(
                conversationMapper, memberMapper, userConversationMapper, memberService,
                profileService, identityValidator, housePublishProvider);

        ConversationListReqDTO request = new ConversationListReqDTO();
        request.setCount(10);
        request.setLastSessionTime(1700000000000L);
        request.setLastSessionId("99");
        when(userConversationMapper.selectRecentByUser(10L, 1700000000000L, 99L, 10))
                .thenReturn(List.of());

        service.list(10L, request);

        verify(userConversationMapper).selectRecentByUser(10L, 1700000000000L, 99L, 10);
    }

    private AppUserVO user(Long id, String nickname) {
        AppUserVO user = new AppUserVO();
        user.setId(id);
        user.setNickname(nickname);
        return user;
    }
}
