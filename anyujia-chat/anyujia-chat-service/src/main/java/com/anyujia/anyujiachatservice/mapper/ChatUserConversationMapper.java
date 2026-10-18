package com.anyujia.anyujiachatservice.mapper;

import com.anyujia.anyujiachatservice.domain.entity.ChatUserConversation;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatUserConversationMapper {
    @Insert("""
            INSERT IGNORE INTO chat_user_conversation
              (user_id, conversation_id, role_type, other_user_id, house_id, house_publish_id,
               unread_count, status, created_at, updated_at)
            VALUES
              (#{userId}, #{conversationId}, #{roleType}, #{otherUserId}, #{houseId}, #{housePublishId},
               #{unreadCount}, #{status}, #{createdAt}, #{updatedAt})
            """)
    int insertIgnore(ChatUserConversation conversation);

    @Select("""
            SELECT user_id, conversation_id, role_type, other_user_id, house_id, house_publish_id,
                   last_message_id, last_message_seq, last_message_time, unread_count,
                   last_read_message_id, last_read_conversation_seq, status, created_at, updated_at
              FROM chat_user_conversation
             WHERE user_id = #{userId}
               AND status = 1
               AND last_message_id IS NOT NULL
               AND (#{cursorLastMessageTime} IS NULL OR #{cursorConversationId} IS NULL
                    OR last_message_time < #{cursorLastMessageTime}
                    OR (last_message_time = #{cursorLastMessageTime}
                        AND conversation_id < #{cursorConversationId}))
             ORDER BY last_message_time DESC, conversation_id DESC
             LIMIT #{count}
            """)
    List<ChatUserConversation> selectRecentByUser(@Param("userId") Long userId,
                                                   @Param("cursorLastMessageTime") Long cursorLastMessageTime,
                                                   @Param("cursorConversationId") Long cursorConversationId,
                                                   @Param("count") int count);

    @Update("""
            UPDATE chat_user_conversation
               SET last_message_id = #{messageId},
                   last_message_seq = #{messageSeq},
                   last_message_time = #{messageTime},
                   updated_at = CURRENT_TIMESTAMP
             WHERE user_id = #{userId}
               AND conversation_id = #{conversationId}
            """)
    int touchMessage(@Param("userId") Long userId,
                     @Param("conversationId") Long conversationId,
                     @Param("messageId") Long messageId,
                     @Param("messageSeq") Long messageSeq,
                     @Param("messageTime") Long messageTime);

    @Update("""
            <script>
            UPDATE chat_user_conversation
               SET last_message_id = #{messageId},
                   last_message_seq = #{messageSeq},
                   last_message_time = #{messageTime},
                   updated_at = CURRENT_TIMESTAMP
             WHERE conversation_id = #{conversationId}
               AND user_id IN
            <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                #{userId}
            </foreach>
            </script>
            """)
    int touchMessageForUsers(@Param("userIds") List<Long> userIds,
                             @Param("conversationId") Long conversationId,
                             @Param("messageId") Long messageId,
                             @Param("messageSeq") Long messageSeq,
                             @Param("messageTime") Long messageTime);

    @Update("""
            UPDATE chat_user_conversation
               SET unread_count = 0,
                   last_read_message_id = #{messageId},
                   last_read_conversation_seq = #{messageSeq},
                   updated_at = CURRENT_TIMESTAMP
             WHERE conversation_id = #{conversationId}
               AND user_id = #{userId}
            """)
    int markVisited(@Param("conversationId") Long conversationId,
                    @Param("userId") Long userId,
                    @Param("messageId") Long messageId,
                    @Param("messageSeq") Long messageSeq);

    @Update("""
            UPDATE chat_user_conversation
               SET unread_count = #{unreadCount}, updated_at = CURRENT_TIMESTAMP
             WHERE user_id = #{userId}
               AND conversation_id = #{conversationId}
            """)
    int updateUnread(@Param("userId") Long userId,
                     @Param("conversationId") Long conversationId,
                     @Param("unreadCount") long unreadCount);
}
