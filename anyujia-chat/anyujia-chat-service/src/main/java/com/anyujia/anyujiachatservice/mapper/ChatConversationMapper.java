package com.anyujia.anyujiachatservice.mapper;

import com.anyujia.anyujiachatservice.domain.entity.ChatConversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
    @Insert("""
            INSERT IGNORE INTO chat_conversation
              (id, tenant_user_id, landlord_user_id, house_id, house_publish_id, status, created_at, updated_at)
            VALUES
              (#{id}, #{tenantUserId}, #{landlordUserId}, #{houseId}, #{housePublishId}, #{status}, #{createdAt}, #{updatedAt})
            """)
    int insertIgnore(ChatConversation conversation);

    @Select("""
            SELECT next_message_seq
              FROM chat_conversation
             WHERE id = #{conversationId}
             FOR UPDATE
            """)
    Long selectNextMessageSeqForUpdate(@Param("conversationId") Long conversationId);

    @Select("""
            SELECT next_message_seq
              FROM chat_conversation
             WHERE id = #{conversationId}
            """)
    Long selectNextMessageSeq(@Param("conversationId") Long conversationId);

    @Update("""
            UPDATE chat_conversation
               SET next_message_seq = #{nextSequence}
             WHERE id = #{conversationId}
            """)
    int updateNextMessageSeq(@Param("conversationId") Long conversationId,
                             @Param("nextSequence") Long nextSequence);

    /** Advances sequence/latest-message metadata without allowing an older transaction to regress it. */
    @Update("""
            UPDATE chat_conversation
               SET last_message_id = CASE WHEN COALESCE(last_message_seq, 0) < #{sequence}
                                          THEN #{messageId} ELSE last_message_id END,
                   last_message_time = CASE WHEN COALESCE(last_message_seq, 0) < #{sequence}
                                            THEN #{lastMessageTime} ELSE last_message_time END,
                   updated_at = CASE WHEN COALESCE(last_message_seq, 0) < #{sequence}
                                     THEN #{updatedAt} ELSE updated_at END,
                   next_message_seq = GREATEST(COALESCE(next_message_seq, 0), #{sequence}),
                   last_message_seq = GREATEST(COALESCE(last_message_seq, 0), #{sequence})
             WHERE id = #{conversationId}
            """)
    int advanceLatestMessage(@Param("conversationId") Long conversationId,
                             @Param("messageId") Long messageId,
                             @Param("sequence") Long sequence,
                             @Param("lastMessageTime") Long lastMessageTime,
                             @Param("updatedAt") LocalDateTime updatedAt);
}
