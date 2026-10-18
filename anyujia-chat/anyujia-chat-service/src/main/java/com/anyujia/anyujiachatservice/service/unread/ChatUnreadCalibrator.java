package com.anyujia.anyujiachatservice.service.unread;

import com.anyujia.anyujiachatservice.mapper.ChatConversationMemberMapper;
import com.anyujia.anyujiachatservice.mapper.ChatUserConversationMapper;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically flushes Redis unread counters back to MySQL.
 *
 * <p>Redis is the hot-path unread store; this job reconciles the database so the unread count
 * survives Redis restarts and remains readable by the conversation list. Only conversations
 * marked dirty by {@link ChatUnreadService} are flushed.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatUnreadCalibrator {
    private final ChatUnreadService unreadService;
    private final ChatUserConversationMapper userConversationMapper;
    private final ChatConversationMemberMapper memberMapper;

    @Scheduled(fixedDelayString = "${chat.unread.calibrate-millis:5000}")
    public void calibrate() {
        Set<String> members = unreadService.dirtyMembers();
        if (members.isEmpty()) {
            return;
        }
        for (String member : members) {
            Long[] pair = unreadService.parseDirtyMember(member);
            if (pair == null) {
                unreadService.markFlushed(member);
                continue;
            }
            Long userId = pair[0];
            Long conversationId = pair[1];
            Long count = unreadService.readCount(userId, conversationId);
            long value = count == null ? 0L : count;
            try {
                userConversationMapper.updateUnread(userId, conversationId, value);
                memberMapper.updateUnread(userId, conversationId, value);
                unreadService.markFlushed(member);
            } catch (RuntimeException ex) {
                log.warn("Failed to calibrate unread, userId={}, conversationId={}", userId, conversationId, ex);
            }
        }
    }
}
