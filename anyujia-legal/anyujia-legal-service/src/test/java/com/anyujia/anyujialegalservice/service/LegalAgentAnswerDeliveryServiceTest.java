package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiachatapi.domain.dto.AgentMessageReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiachatapi.feign.ChatFeignClient;
import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.domain.entity.LegalAgentTask;
import com.anyujia.anyujialegalservice.mapper.LegalAgentTaskMapper;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegalAgentAnswerDeliveryServiceTest {
    @Test
    void committedTaskUsesStableDeliveryKey() {
        LegalAgentTaskMapper mapper = mock(LegalAgentTaskMapper.class);
        ChatFeignClient chat = mock(ChatFeignClient.class);
        LegalAgentMemoryService memory = mock(LegalAgentMemoryService.class);
        LegalAgentAnswerDeliveryService service = new LegalAgentAnswerDeliveryService(
                mapper, chat, new ObjectMapper(), memory, new LegalAiProperties(), Runnable::run);
        LegalAgentTask task = new LegalAgentTask();
        task.setId(55L);
        task.setTenantId(1L);
        task.setChatConversationId(2L);
        task.setQueryText("押金");
        task.setAnswerJson("{\"answerText\":\"七日内退还\",\"contextConflict\":false}");
        when(mapper.claimChatDelivery(55L)).thenReturn(1);
        when(mapper.selectById(55L)).thenReturn(task);
        when(mapper.markChatDelivered(55L)).thenReturn(1);
        when(chat.sendAgentMessage(eq(1L), eq("service"), any()))
                .thenReturn(ApiResult.success(new MessageVO()));

        service.deliver(55L);

        ArgumentCaptor<AgentMessageReqDTO> sent = ArgumentCaptor.forClass(AgentMessageReqDTO.class);
        verify(chat).sendAgentMessage(eq(1L), eq("service"), sent.capture());
        assertThat(sent.getValue().getRequestId()).isEqualTo("legal-agent-55");
        verify(mapper).markChatDelivered(55L);
        verify(memory).appendTurn(1L, 2L, "assistant", "七日内退还");
    }

    @Test
    void unclaimedTaskNeverSendsChatMessage() {
        LegalAgentTaskMapper mapper = mock(LegalAgentTaskMapper.class);
        ChatFeignClient chat = mock(ChatFeignClient.class);
        LegalAgentAnswerDeliveryService service = new LegalAgentAnswerDeliveryService(
                mapper, chat, new ObjectMapper(), mock(LegalAgentMemoryService.class),
                new LegalAiProperties(), Runnable::run);

        service.deliver(99L);

        verify(chat, never()).sendAgentMessage(any(), any(), any());
    }
}
