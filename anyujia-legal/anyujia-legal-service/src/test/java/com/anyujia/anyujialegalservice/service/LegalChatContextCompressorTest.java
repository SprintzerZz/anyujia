package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegalChatContextCompressorTest {
    @Test
    void prioritizesRiskFactsAndKeepsMessageLocator() {
        MessageVO normal = message("m-1", 1L, "好的，明天看房。", 1L);
        MessageVO risk = message("m-2", 2L, "押金 3000 元，入住前维修空调，退租前结清。", 2L);

        String context = new LegalChatContextCompressor().build(
                1L, List.of(normal, risk), "押金什么时候退");

        assertThat(context).contains("id=m-2", "amount=3000 元", "date=入住前", "押金 3000 元");
        assertThat(context).doesNotContain("id=m-1");
    }

    @Test
    void keepsRecentMessagesWhenNoRiskTermMatches() {
        MessageVO first = message("m-1", 1L, "先确认地址。", 1L);
        MessageVO second = message("m-2", 1L, "周末方便签字。", 2L);

        String context = new LegalChatContextCompressor().build(
                1L, List.of(first, second), "其他问题");

        assertThat(context).contains("id=m-1", "id=m-2");
    }

    private MessageVO message(String id, Long fromId, String content, long seq) {
        MessageVO message = new MessageVO();
        message.setMessageId(id);
        message.setFromId(fromId);
        message.setContent(content);
        message.setConversationSeq(String.valueOf(seq));
        message.setCreateTime(seq);
        return message;
    }
}
