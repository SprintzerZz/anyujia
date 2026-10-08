package com.anyujia.anyujiachatservice.cache;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import java.util.List;
import java.util.function.Supplier;

public interface RecentMessageCache {
    List<MessageVO> getRecent(Long conversationId, int count, Supplier<List<MessageVO>> loader);

    void append(MessageVO message);
}
