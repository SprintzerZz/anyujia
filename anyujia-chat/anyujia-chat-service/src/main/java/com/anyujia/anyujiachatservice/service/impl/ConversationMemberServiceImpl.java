package com.anyujia.anyujiachatservice.service.impl;

import com.anyujia.anyujiachatapi.constants.ChatConstants;
import com.anyujia.anyujiachatservice.domain.entity.ChatConversation;
import com.anyujia.anyujiachatservice.domain.entity.ChatConversationMember;
import com.anyujia.anyujiachatservice.mapper.ChatConversationMapper;
import com.anyujia.anyujiachatservice.mapper.ChatConversationMemberMapper;
import com.anyujia.anyujiachatservice.service.ConversationMemberService;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConversationMemberServiceImpl implements ConversationMemberService {
    private final ChatConversationMemberMapper memberMapper;
    private final ChatConversationMapper conversationMapper;

    @Override
    public ChatConversationMember requireMember(Long conversationId, Long userId) {
        ChatConversationMember member = memberMapper.selectOne(new LambdaQueryWrapper<ChatConversationMember>()
                .eq(ChatConversationMember::getConversationId, conversationId)
                .eq(ChatConversationMember::getUserId, userId));
        if (member == null) {
            throw new ServiceException("You are not a member of this conversation",
                    ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
        return member;
    }

    @Override
    public Long requireCounterparty(Long conversationId, Long userId) {
        requireMember(conversationId, userId);
        ChatConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation != null && Integer.valueOf(ChatConstants.CONVERSATION_TYPE_AGENT)
                .equals(conversation.getConversationType())) {
            return ChatConstants.AGENT_USER_ID;
        }
        ChatConversationMember member = memberMapper.selectOne(new LambdaQueryWrapper<ChatConversationMember>()
                .eq(ChatConversationMember::getConversationId, conversationId)
                .ne(ChatConversationMember::getUserId, userId));
        if (member == null) {
            throw new ServiceException("Conversation participant data is incomplete");
        }
        return member.getUserId();
    }
}
