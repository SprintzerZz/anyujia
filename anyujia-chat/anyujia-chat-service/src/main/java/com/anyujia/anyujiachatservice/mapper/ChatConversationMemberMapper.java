package com.anyujia.anyujiachatservice.mapper;

import com.anyujia.anyujiachatservice.domain.entity.ChatConversationMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatConversationMemberMapper extends BaseMapper<ChatConversationMember> {
    @Update("""
            UPDATE chat_conversation_member
               SET unread_count = unread_count + 1, updated_at = CURRENT_TIMESTAMP
             WHERE conversation_id = #{conversationId} AND user_id = #{userId}
            """)
    int incrementUnread(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Update("""
            UPDATE chat_conversation_member
               SET unread_count = #{unreadCount}, updated_at = CURRENT_TIMESTAMP
             WHERE user_id = #{userId} AND conversation_id = #{conversationId}
            """)
    int updateUnread(@Param("userId") Long userId,
                     @Param("conversationId") Long conversationId,
                     @Param("unreadCount") long unreadCount);
}
