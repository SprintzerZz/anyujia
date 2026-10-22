package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiachatapi.domain.dto.AgentMessageReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiachatapi.feign.ChatFeignClient;
import com.anyujia.anyujialegalapi.domain.dto.LegalAgentAnswerReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.LegalAgentAnswerVO;
import com.anyujia.anyujialegalapi.domain.vo.LegalCitationVO;
import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.domain.entity.LegalAgentConversation;
import com.anyujia.anyujialegalservice.domain.entity.LegalContract;
import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import com.anyujia.anyujialegalservice.domain.model.LegalAnswerGeneration;
import com.anyujia.anyujialegalservice.domain.model.RiskFinding;
import com.anyujia.anyujialegalservice.mapper.LegalContractMapper;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalAgentService {
    private static final int MAX_GROUNDING_LENGTH = 24000;

    private final LegalAgentConversationService conversationService;
    private final LegalContractMapper contractMapper;
    private final ChatEvidenceProvider chatEvidenceProvider;
    private final RentalProductEvidenceProvider productEvidenceProvider;
    private final LegalKnowledgeRetriever knowledgeRetriever;
    private final LegalAnswerGenerator answerGenerator;
    private final LegalContractContextCompressor contractContextCompressor;
    private final LegalChatContextCompressor chatContextCompressor;
    private final LegalAgentMemoryService memoryService;
    private final LegalAiProperties properties;
    private final ChatFeignClient chatFeignClient;

    public LegalAgentAnswerVO answer(Long tenantId, LegalAgentAnswerReqDTO request) {
        LegalAgentConversation binding = requireBinding(tenantId, request.getChatConversationId());
        LegalContract contract = contractMapper.selectOne(new LambdaQueryWrapper<LegalContract>()
                .eq(LegalContract::getId, binding.getContractId())
                .eq(LegalContract::getTenantId, tenantId));
        if (contract == null) {
            throw new ServiceException("合同不存在", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
        String grounding = groundingContext(tenantId, binding, contract, request.getQuery());
        String regionCode = request.getRegionCode() == null || request.getRegionCode().isBlank()
                ? "GLOBAL" : request.getRegionCode().toUpperCase();
        LocalDate asOfDate = request.getAsOfDate() == null || request.getAsOfDate().isBlank()
                ? LocalDate.now() : LocalDate.parse(request.getAsOfDate());
        List<LawPassage> passages = knowledgeRetriever.retrieve(request.getQuery(), regionCode, asOfDate, 5);
        LegalAnswerGeneration generation = answerGenerator.generateGrounded(request.getQuery(), passages, grounding);

        memoryService.appendTurn(tenantId, request.getChatConversationId(), "user", request.getQuery());
        memoryService.appendTurn(tenantId, request.getChatConversationId(), "assistant", generation.answer());
        injectReply(tenantId, request.getChatConversationId(), generation.answer());

        LegalAgentAnswerVO vo = new LegalAgentAnswerVO();
        vo.setAnswerText(generation.answer());
        vo.setCitations(citations(generation.citationIndexes(), passages));
        vo.setMode(generation.mode());
        vo.setDegraded(generation.degraded());
        return vo;
    }

    public void notifyAssessmentCompleted(Long contractId, List<RiskFinding> findings) {
        LegalAgentConversation binding = conversationService.findLatestByContractId(contractId);
        if (binding == null) {
            return;
        }
        injectReply(binding.getTenantId(), binding.getChatConversationId(), naturalize(findings));
    }

    private void injectReply(Long tenantId, Long chatConversationId, String content) {
        try {
            AgentMessageReqDTO message = new AgentMessageReqDTO();
            message.setChatConversationId(chatConversationId);
            message.setContent(bounded(content, properties.getAgent().getAnswerMaxLength()));
            chatFeignClient.sendAgentMessage(tenantId, "service", message);
        } catch (Exception ex) {
            log.warn("Failed to inject legal agent message for conversation {}: {}", chatConversationId, ex.getMessage());
        }
    }

    private LegalAgentConversation requireBinding(Long tenantId, Long chatConversationId) {
        LegalAgentConversation binding = conversationService.findByChatConversationId(chatConversationId);
        if (binding == null || !binding.getTenantId().equals(tenantId)
                || !Integer.valueOf(1).equals(binding.getStatus())) {
            throw new ServiceException("法律助手会话不存在或无权访问", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
        return binding;
    }

    private String groundingContext(Long tenantId, LegalAgentConversation binding, LegalContract contract,
                                    String query) {
        StringBuilder result = new StringBuilder("[contract]\n");
        result.append(contractContextCompressor.build(contract.getId(), contract.getExtractedText(), query))
                .append('\n');
        result.append("[landlord_messages]\n");
        List<MessageVO> messages = chatEvidenceProvider.recentMessages(tenantId, binding.getLandlordConversationId(), 100);
        result.append(chatContextCompressor.build(tenantId, messages, query)).append('\n');
        result.append("[rental_product]\n");
        productEvidenceProvider.snapshot(tenantId, binding.getLandlordConversationId()).ifPresent(product -> {
            result.append("depositMonths=").append(product.getDepositMonths()).append('\n');
            result.append("monthlyRent=").append(product.getMonthlyRent()).append('\n');
            result.append("paymentCycleMonths=").append(product.getPaymentCycleMonths()).append('\n');
            result.append("description=").append(product.getDescription()).append('\n');
        });
        return bounded(result.toString(), MAX_GROUNDING_LENGTH);
    }

    private List<LegalCitationVO> citations(List<Integer> indexes, List<LawPassage> passages) {
        if (indexes == null) {
            return List.of();
        }
        return indexes.stream().filter(index -> index != null && index >= 1 && index <= passages.size())
                .map(index -> citation(index, passages.get(index - 1))).toList();
    }

    private LegalCitationVO citation(int index, LawPassage passage) {
        return LegalCitationVO.builder()
                .index(index)
                .chunkId(passage.chunkId())
                .sourceId(passage.sourceId())
                .title(passage.title())
                .articleNumber(passage.articleNumber())
                .quote(passage.text())
                .sourceUrl(passage.sourceUrl())
                .build();
    }

    private String naturalize(List<RiskFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return "已完成合同风险分析，未命中当前规则库中的明显风险，建议签约前人工核对合同。";
        }
        StringBuilder result = new StringBuilder("已完成合同风险分析，共识别 ")
                .append(findings.size()).append(" 项风险：");
        for (int i = 0; i < findings.size(); i++) {
            RiskFinding finding = findings.get(i);
            result.append('\n').append(i + 1).append(". ").append(finding.getTitle());
            if (finding.getConclusion() != null && !finding.getConclusion().isBlank()) {
                result.append("：").append(finding.getConclusion());
            }
        }
        result.append("\n你可以继续追问具体条款或应对建议。");
        return result.toString();
    }

    private String bounded(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.substring(0, Math.min(value.length(), maxLength));
    }
}
