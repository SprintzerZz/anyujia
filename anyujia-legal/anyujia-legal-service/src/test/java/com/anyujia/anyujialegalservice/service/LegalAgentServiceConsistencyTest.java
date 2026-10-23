package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiachatapi.feign.ChatFeignClient;
import com.anyujia.anyujialegalapi.domain.dto.LegalAgentAnswerReqDTO;
import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.domain.entity.LegalAgentConversation;
import com.anyujia.anyujialegalservice.domain.entity.LegalContract;
import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import com.anyujia.anyujialegalservice.domain.model.LegalAnswerGeneration;
import com.anyujia.anyujialegalservice.mapper.LegalContractMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegalAgentServiceConsistencyTest {
    @Test
    void changedContractSuppressesGeneratedAnswerAndChatPublication() {
        LegalAgentConversationService conversations = mock(LegalAgentConversationService.class);
        LegalContractMapper contracts = mock(LegalContractMapper.class);
        ChatEvidenceProvider chat = mock(ChatEvidenceProvider.class);
        RentalProductEvidenceProvider products = mock(RentalProductEvidenceProvider.class);
        LegalKnowledgeRetriever retriever = mock(LegalKnowledgeRetriever.class);
        LegalAnswerGenerator generator = mock(LegalAnswerGenerator.class);
        LegalContractContextCompressor contractCompressor = mock(LegalContractContextCompressor.class);
        LegalChatContextCompressor chatCompressor = mock(LegalChatContextCompressor.class);
        LegalAgentMemoryService memory = mock(LegalAgentMemoryService.class);
        LegalAgentHistoryCompressor history = mock(LegalAgentHistoryCompressor.class);
        ChatFeignClient chatClient = mock(ChatFeignClient.class);
        LegalAgentService service = new LegalAgentService(conversations, contracts, chat, products,
                retriever, generator, contractCompressor, chatCompressor, memory, history,
                new LegalAgentContextVersionService(null), new LegalAiProperties(), chatClient);

        LegalAgentConversation binding = new LegalAgentConversation();
        binding.setTenantId(1L);
        binding.setChatConversationId(2L);
        binding.setLandlordConversationId(3L);
        binding.setContractId(4L);
        binding.setStatus(1);
        when(conversations.findByChatConversationId(2L)).thenReturn(binding);
        LegalContract oldContract = new LegalContract();
        oldContract.setId(4L);
        oldContract.setExtractedText("旧押金条款");
        LegalContract newContract = new LegalContract();
        newContract.setId(4L);
        newContract.setExtractedText("新押金条款");
        when(contracts.selectOne(any())).thenReturn(oldContract, newContract);
        when(chat.freshMessages(1L, 3L, 100)).thenReturn(List.of());
        when(products.snapshot(1L, 3L)).thenReturn(Optional.empty());
        when(contractCompressor.build(eq(4L), any(), eq("押金"), anyInt())).thenReturn("相同的压缩片段");
        when(retriever.retrieve(any(), any(), any(), anyInt()))
                .thenReturn(List.of(new LawPassage(1L, 2L, "法律", "1", "证据", "", 1.0)));
        when(generator.generateGrounded(any(), any(), any()))
                .thenReturn(new LegalAnswerGeneration("基于旧合同的判断", List.of(1), "GENERATIVE_RAG", true, false));

        LegalAgentAnswerReqDTO request = new LegalAgentAnswerReqDTO();
        request.setChatConversationId(2L);
        request.setQuery("押金");
        var answer = service.answer(1L, request);
        service.publishAnswer(1L, request, answer);

        assertThat(answer.isContextConflict()).isTrue();
        assertThat(answer.getContextStatus()).isEqualTo("STALE_CONTEXT");
        assertThat(answer.getAnswerText()).doesNotContain("基于旧合同的判断");
        assertThat(answer.getCitations()).isEmpty();
        verify(memory, never()).appendTurn(any(), any(), any(), any());
        verify(chatClient, never()).sendAgentMessage(any(), any(), any());
    }
}
