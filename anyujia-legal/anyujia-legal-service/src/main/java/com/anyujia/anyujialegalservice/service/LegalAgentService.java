package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiachatapi.domain.dto.AgentMessageReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujiahouseapi.domain.vo.HousePublishVO;
import com.anyujia.anyujiachatapi.feign.ChatFeignClient;
import com.anyujia.anyujialegalapi.domain.dto.LegalAgentAnswerReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.LegalAgentAnswerVO;
import com.anyujia.anyujialegalapi.domain.vo.LegalCitationVO;
import com.anyujia.anyujialegalapi.domain.vo.LegalConclusionSupportVO;
import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.domain.entity.LegalAgentConversation;
import com.anyujia.anyujialegalservice.domain.entity.LegalContract;
import com.anyujia.anyujialegalservice.domain.model.AgentHistory;
import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import com.anyujia.anyujialegalservice.domain.model.LegalAnswerGeneration;
import com.anyujia.anyujialegalservice.domain.model.RiskFinding;
import com.anyujia.anyujialegalservice.mapper.LegalContractMapper;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@Slf4j
@Service
public class LegalAgentService {
    private static final int MAX_GROUNDING_LENGTH = 24000;
    private static final Pattern CONTRACT_BLOCK_ID = Pattern.compile("\\[contract_block id=([^ ]+)");

    private final LegalAgentConversationService conversationService;
    private final LegalContractMapper contractMapper;
    private final ChatEvidenceProvider chatEvidenceProvider;
    private final RentalProductEvidenceProvider productEvidenceProvider;
    private final LegalKnowledgeRetriever knowledgeRetriever;
    private final LegalAnswerGenerator answerGenerator;
    private final LegalContractContextCompressor contractContextCompressor;
    private final LegalChatContextCompressor chatContextCompressor;
    private final LegalAgentMemoryService memoryService;
    private final LegalAgentHistoryCompressor agentHistoryCompressor;
    private final LegalAgentContextVersionService contextVersionService;
    private final LegalAiProperties properties;
    private final ChatFeignClient chatFeignClient;
    private final LegalConclusionSupportEvaluator supportEvaluator;

    @Autowired
    public LegalAgentService(LegalAgentConversationService conversationService,
                             LegalContractMapper contractMapper, ChatEvidenceProvider chatEvidenceProvider,
                             RentalProductEvidenceProvider productEvidenceProvider,
                             LegalKnowledgeRetriever knowledgeRetriever, LegalAnswerGenerator answerGenerator,
                             LegalContractContextCompressor contractContextCompressor,
                             LegalChatContextCompressor chatContextCompressor,
                             LegalAgentMemoryService memoryService, LegalAgentHistoryCompressor agentHistoryCompressor,
                             LegalAgentContextVersionService contextVersionService, LegalAiProperties properties,
                             ChatFeignClient chatFeignClient, LegalConclusionSupportEvaluator supportEvaluator) {
        this.conversationService = conversationService;
        this.contractMapper = contractMapper;
        this.chatEvidenceProvider = chatEvidenceProvider;
        this.productEvidenceProvider = productEvidenceProvider;
        this.knowledgeRetriever = knowledgeRetriever;
        this.answerGenerator = answerGenerator;
        this.contractContextCompressor = contractContextCompressor;
        this.chatContextCompressor = chatContextCompressor;
        this.memoryService = memoryService;
        this.agentHistoryCompressor = agentHistoryCompressor;
        this.contextVersionService = contextVersionService;
        this.properties = properties;
        this.chatFeignClient = chatFeignClient;
        this.supportEvaluator = supportEvaluator;
    }

    public LegalAgentService(LegalAgentConversationService conversationService,
                             LegalContractMapper contractMapper, ChatEvidenceProvider chatEvidenceProvider,
                             RentalProductEvidenceProvider productEvidenceProvider,
                             LegalKnowledgeRetriever knowledgeRetriever, LegalAnswerGenerator answerGenerator,
                             LegalContractContextCompressor contractContextCompressor,
                             LegalChatContextCompressor chatContextCompressor,
                             LegalAgentMemoryService memoryService, LegalAgentHistoryCompressor agentHistoryCompressor,
                             LegalAgentContextVersionService contextVersionService, LegalAiProperties properties,
                             ChatFeignClient chatFeignClient) {
        this(conversationService, contractMapper, chatEvidenceProvider, productEvidenceProvider, knowledgeRetriever,
                answerGenerator, contractContextCompressor, chatContextCompressor, memoryService,
                agentHistoryCompressor, contextVersionService, properties, chatFeignClient,
                new LegalConclusionSupportEvaluator());
    }

    public LegalAgentAnswerVO answer(Long tenantId, LegalAgentAnswerReqDTO request) {
        LegalAgentConversation binding = requireBinding(tenantId, request.getChatConversationId());
        EvidenceSnapshot before = evidenceSnapshot(tenantId, binding, request.getQuery());
        String grounding = before.grounding();
        LegalAgentContextVersionService.ContextVersion contextVersion = contextVersionService.recordVersion(
                tenantId, binding.getChatConversationId(), before.sourceFingerprint());
        if (contextVersion.status() == LegalAgentContextVersionService.ChangeStatus.CHANGED) {
            grounding = "[context_version changed=true; favor_latest_evidence]\n" + grounding;
        }
        String regionCode = request.getRegionCode() == null || request.getRegionCode().isBlank()
                ? "GLOBAL" : request.getRegionCode().toUpperCase();
        LocalDate asOfDate = request.getAsOfDate() == null || request.getAsOfDate().isBlank()
                ? LocalDate.now() : LocalDate.parse(request.getAsOfDate());
        List<LawPassage> passages = knowledgeRetriever.retrieve(request.getQuery(), regionCode, asOfDate, 5);
        LegalAnswerGeneration generation = answerGenerator.generateGrounded(request.getQuery(), passages, grounding);
        EvidenceSnapshot after = evidenceSnapshot(tenantId, binding, request.getQuery());
        boolean contextConflict = !before.consistencyFingerprint().equals(after.consistencyFingerprint());

        LegalAgentAnswerVO vo = new LegalAgentAnswerVO();
        vo.setAnswerText(contextConflict ? "分析期间合同、聊天或房源信息发生变化，请重新提问以获取最新风险判断。" : generation.answer());
        vo.setCitations(contextConflict ? List.of() : citations(generation.citationIndexes(), passages));
        List<LegalConclusionSupportVO> supports = contextConflict ? List.of()
                : supportEvaluator.evaluate(generation.answer(), vo.getCitations());
        vo.setConclusionSupports(supports);
        vo.setReviewRequired(supportEvaluator.minimumScore(supports).doubleValue() < 0.30D);
        vo.setMode(generation.mode());
        vo.setDegraded(generation.degraded());
        vo.setContextHash(contextVersion.hash());
        vo.setContextStatus(contextConflict ? "STALE_CONTEXT" : contextVersion.status().name());
        vo.setContextConflict(contextConflict);
        Map<String, Object> manifest = new LinkedHashMap<>(before.manifest());
        manifest.put("regionCode", regionCode);
        manifest.put("asOfDate", asOfDate.toString());
        manifest.put("chatModel", properties.getChatModel());
        manifest.put("promptVersion", "grounded-v1");
        manifest.put("lawPassages", passages.stream().map(passage -> Map.of(
                "chunkId", String.valueOf(passage.chunkId()),
                "sourceId", String.valueOf(passage.sourceId()),
                "textSha256", contextVersionService.sha256(passage.text()))).toList());
        vo.setEvidenceManifest(manifest);
        return vo;
    }

    /** Only publish a committed, non-stale answer. */
    public void publishAnswer(Long tenantId, LegalAgentAnswerReqDTO request, LegalAgentAnswerVO answer) {
        if (answer.isContextConflict()) {
            return;
        }
        memoryService.appendTurn(tenantId, request.getChatConversationId(), "user", request.getQuery());
        memoryService.appendTurn(tenantId, request.getChatConversationId(), "assistant", answer.getAnswerText());
        injectReply(tenantId, request.getChatConversationId(), answer.getAnswerText());
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

    private EvidenceSnapshot evidenceSnapshot(Long tenantId, LegalAgentConversation binding, String query) {
        LegalContract contract = contractMapper.selectOne(new LambdaQueryWrapper<LegalContract>()
                .eq(LegalContract::getId, binding.getContractId())
                .eq(LegalContract::getTenantId, tenantId));
        if (contract == null) {
            throw new ServiceException("合同不存在", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
        List<MessageVO> messages = chatEvidenceProvider.freshMessages(
                tenantId, binding.getLandlordConversationId(), 100);
        Optional<HousePublishVO> product = productEvidenceProvider.snapshot(
                tenantId, binding.getLandlordConversationId());
        StringBuilder result = new StringBuilder("[contract]\n");
        result.append(contractContextCompressor.build(contract.getId(), contract.getExtractedText(), query, 12000))
                .append('\n');
        result.append("[landlord_messages]\n");
        result.append(chatContextCompressor.build(tenantId, messages, query, 6000)).append('\n');
        List<AgentHistory.Turn> history = memoryService.getHistory(tenantId, binding.getChatConversationId());
        result.append(agentHistoryCompressor.build(history)).append('\n');
        result.append("[rental_product]\n");
        product.ifPresent(value -> {
            result.append("depositMonths=").append(value.getDepositMonths()).append('\n');
            result.append("monthlyRent=").append(value.getMonthlyRent()).append('\n');
            result.append("paymentCycleMonths=").append(value.getPaymentCycleMonths()).append('\n');
            result.append("description=").append(bounded(value.getDescription(), 1600)).append('\n');
        });
        StringBuilder sources = new StringBuilder();
        sources.append(contract.getId()).append('|').append(contract.getSha256()).append('|')
                .append(contract.getUpdatedAt()).append('|').append(contract.getExtractedText());
        for (MessageVO message : messages) {
            sources.append('|').append(message.getMessageId()).append(':')
                    .append(message.getConversationSeq()).append(':').append(message.getContent());
        }
        product.ifPresent(value -> sources.append('|').append(value.getPublishId()).append(':')
                .append(value.getMonthlyRent()).append(':').append(value.getDepositMonths()).append(':')
                .append(value.getPaymentCycleMonths()).append(':').append(value.getDescription()));
        for (AgentHistory.Turn turn : history) {
            sources.append('|').append(turn.getRole()).append(':').append(turn.getContent());
        }
        String sourceFingerprint = contextVersionService.sha256(sources.toString());
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("tenantId", tenantId);
        manifest.put("agentConversationId", binding.getChatConversationId());
        manifest.put("landlordConversationId", binding.getLandlordConversationId());
        manifest.put("contractId", contract.getId());
        manifest.put("contractSha256", contract.getSha256());
        manifest.put("contractUpdatedAt", String.valueOf(contract.getUpdatedAt()));
        List<String> selectedBlockIds = new java.util.ArrayList<>();
        Matcher selectedBlocks = CONTRACT_BLOCK_ID.matcher(result);
        while (selectedBlocks.find()) {
            selectedBlockIds.add(selectedBlocks.group(1));
        }
        manifest.put("selectedContractBlockIds", selectedBlockIds);
        manifest.put("chatMessageIds", messages.stream().map(MessageVO::getMessageId).toList());
        manifest.put("chatConversationSeqs", messages.stream().map(MessageVO::getConversationSeq).toList());
        manifest.put("productPublishId", product.map(HousePublishVO::getPublishId).orElse(null));
        manifest.put("sourceFingerprint", sourceFingerprint);
        return new EvidenceSnapshot(bounded(result.toString(), MAX_GROUNDING_LENGTH),
                sourceFingerprint, contextVersionService.sha256(sources.append('|').append(result).toString()),
                manifest);
    }

    private record EvidenceSnapshot(String grounding, String sourceFingerprint,
                                    String consistencyFingerprint, Map<String, Object> manifest) { }

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
