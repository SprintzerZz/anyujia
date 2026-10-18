package com.anyujia.anyujiachatservice.service.unread;

import com.anyujia.anyujiachatservice.cache.ChatConversationIndexCache;
import com.anyujia.anyujiachatservice.mapper.ChatUserConversationMapper;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Flushes Redis conversation-index updates to MySQL outside the message transaction. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatConversationIndexCalibrator {
    private final ChatConversationIndexCache indexCache;
    private final ChatUserConversationMapper userConversationMapper;

    @Scheduled(fixedDelayString = "${chat.conversation-index.calibrate-millis:5000}")
    public void calibrate() {
        Set<String> dirtyMembers = indexCache.dirtyMembers();
        for (String dirtyMember : dirtyMembers) {
            Long[] pair = indexCache.parseDirtyMember(dirtyMember);
            if (pair == null) {
                continue;
            }
            Long userId = pair[0];
            Long conversationId = pair[1];
            ChatConversationIndexCache.IndexUpdate update;
            try {
                update = indexCache.readUpdate(userId, conversationId);
                if (update == null) {
                    continue;
                }
                userConversationMapper.touchMessage(userId, conversationId,
                        update.messageId(), update.messageSeq(), update.messageTime());
                if (indexCache.isSameUpdate(userId, conversationId, update)) {
                    indexCache.markFlushed(userId, conversationId);
                }
            } catch (RuntimeException ex) {
                log.warn("Failed to calibrate conversation index, userId={}, conversationId={}",
                        userId, conversationId, ex);
            }
        }
    }
}
