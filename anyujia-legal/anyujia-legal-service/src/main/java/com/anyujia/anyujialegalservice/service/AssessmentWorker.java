package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujialegalservice.constants.LegalConstants;
import com.anyujia.anyujialegalservice.domain.entity.LegalAssessmentContextSnapshot;
import com.anyujia.anyujialegalservice.domain.entity.LegalAssessmentJob;
import com.anyujia.anyujialegalservice.domain.entity.LegalContract;
import com.anyujia.anyujialegalservice.domain.entity.LegalDocumentBlock;
import com.anyujia.anyujialegalservice.domain.entity.LegalEvidenceRef;
import com.anyujia.anyujialegalservice.domain.entity.LegalRiskItem;
import com.anyujia.anyujialegalservice.domain.entity.LegalTraceEvent;
import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import com.anyujia.anyujialegalservice.domain.model.RiskFinding;
import com.anyujia.anyujialegalservice.mapper.LegalAssessmentJobMapper;
import com.anyujia.anyujialegalservice.mapper.LegalContractMapper;
import com.anyujia.anyujialegalservice.mapper.LegalDocumentBlockMapper;
import com.anyujia.anyujialegalservice.mapper.LegalEvidenceRefMapper;
import com.anyujia.anyujialegalservice.mapper.LegalRiskItemMapper;
import com.anyujia.anyujiahouseapi.domain.vo.HousePublishVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentWorker {
    private final LegalAssessmentJobMapper jobMapper;
    private final LegalContractMapper contractMapper;
    private final LegalRiskItemMapper riskMapper;
    private final LegalDocumentBlockMapper blockMapper;
    private final LegalEvidenceRefMapper evidenceMapper;
    private final RentalContractRuleEngine ruleEngine;
    private final ChatEvidenceProvider chatEvidenceProvider;
    private final ConversationRiskAnalyzer conversationRiskAnalyzer;
    private final RentalProductEvidenceProvider productEvidenceProvider;
    private final RentalProductRiskAnalyzer productRiskAnalyzer;
    private final LegalKnowledgeRetriever knowledgeRetriever;
    private final RiskAnalysisEnhancer enhancer;
    private final LegalTraceService traceService;
    private final AssessmentContextSnapshotService contextSnapshotService;
    private final LegalAgentService legalAgentService;

    public void execute(Long jobId) {
        LegalAssessmentJob job = jobMapper.selectById(jobId);
        if (job == null || !LegalConstants.JOB_PENDING.equals(job.getStatus())) {
            return;
        }
        updateStatus(jobId, LegalConstants.JOB_RUNNING, null, null, null);
        LegalTraceEvent runTrace = traceService.start(jobId, "ASSESSMENT_RUN",
                details("tenantId", job.getTenantId(), "contractId", job.getContractId(),
                        "regionCode", job.getRegionCode(), "asOfDate", job.getAsOfDate()));
        try {
            LegalTraceEvent contextTrace = traceService.start(jobId, "CONTRACT_CONTEXT",
                    details("contractId", job.getContractId()));
            LegalContract contract = contractMapper.selectById(job.getContractId());
            if (contract == null) {
                IllegalStateException ex = new IllegalStateException("contract not found");
                traceService.failure(contextTrace, ex, details("contractId", job.getContractId()));
                throw ex;
            }
            List<LegalDocumentBlock> blocks = blockMapper.selectList(new LambdaQueryWrapper<LegalDocumentBlock>()
                    .eq(LegalDocumentBlock::getContractId, contract.getId())
                    .orderByAsc(LegalDocumentBlock::getBlockIndex));
            traceService.success(contextTrace, details("blockCount", blocks.size(),
                    "documentType", contract.getDocumentType(),
                    "locatorPrecision", contract.getLocatorPrecision()));

            LegalTraceEvent ruleTrace = traceService.start(jobId, "CONTRACT_RULE_ANALYSIS",
                    details("contractCharacters", contract.getExtractedText() == null
                            ? 0 : contract.getExtractedText().length()));
            List<RiskFinding> deterministic = new ArrayList<>(ruleEngine.analyze(contract.getExtractedText()));
            traceService.success(ruleTrace, details("riskCount", deterministic.size(),
                    "riskCodes", riskCodes(deterministic)));
            deterministic.forEach(finding -> locateContractEvidence(contract, blocks, finding));

            LegalTraceEvent chatTrace = traceService.start(jobId, "CHAT_EVIDENCE_ANALYSIS",
                    details("conversationId", contract.getConversationId(), "messageLimit", 100));
            List<MessageVO> messages = chatEvidenceProvider.recentMessages(job.getTenantId(),
                    contract.getConversationId(), 100);
            List<RiskFinding> chatFindings = conversationRiskAnalyzer.analyze(
                    job.getTenantId(), contract.getExtractedText(), messages);
            deterministic.addAll(chatFindings);
            traceService.success(chatTrace, details("messageCount", messages.size(),
                    "chatRiskCount", chatFindings.size(), "riskCodes", riskCodes(chatFindings)));

            LegalTraceEvent productTrace = traceService.start(jobId, "PRODUCT_CONTEXT",
                    details("conversationId", contract.getConversationId()));
            HousePublishVO product = productEvidenceProvider.snapshot(job.getTenantId(), contract.getConversationId())
                    .orElse(null);
            traceService.success(productTrace, details("available", product != null,
                    "housePublishId", product == null ? null : product.getPublishId(),
                    "depositMonths", product == null ? null : product.getDepositMonths(),
                    "paymentCycleMonths", product == null ? null : product.getPaymentCycleMonths()));
            List<RiskFinding> productFindings = productRiskAnalyzer.analyze(contract.getExtractedText(), product);
            deterministic.addAll(productFindings);
            traceService.record(jobId, "PRODUCT_RISK_ANALYSIS", "SUCCESS",
                    details("riskCount", productFindings.size(), "riskCodes", riskCodes(productFindings)));

            List<RiskFinding> baseFindings = deduplicate(deterministic);
            Map<String, String> queriesByRisk = new LinkedHashMap<>();
            for (RiskFinding finding : baseFindings) {
                queriesByRisk.put(finding.getRiskCode(), legalSearchQuery(finding));
            }
            LegalTraceEvent retrievalTrace = traceService.start(jobId, "LEGAL_RETRIEVAL",
                    details("queryCount", queriesByRisk.size(), "regionCode", job.getRegionCode(),
                            "asOfDate", job.getAsOfDate()));
            Map<String, List<LawPassage>> passagesByRisk = knowledgeRetriever.retrieveBatch(
                    queriesByRisk, job.getRegionCode(), job.getAsOfDate(), 1);
            traceService.success(retrievalTrace, details("queryCount", queriesByRisk.size(),
                    "passageCount", passageCount(passagesByRisk)));
            LegalAssessmentContextSnapshot contextSnapshot = contextSnapshotService.createSnapshot(
                    job, contract, blocks, messages, product, passagesByRisk);
            traceService.record(jobId, "ASSESSMENT_CONTEXT_SNAPSHOT", "SUCCESS",
                    details("contextSnapshotId", contextSnapshot.getId(),
                            "contextHash", contextSnapshot.getContextHash(),
                            "contractBlockCount", contextSnapshot.getContractBlockCount(),
                            "chatMessageCount", contextSnapshot.getChatMessageCount(),
                            "housePublishId", contextSnapshot.getHousePublishId(),
                            "lawPassageCount", contextSnapshot.getLawPassageCount()));
            for (RiskFinding finding : baseFindings) {
                List<LawPassage> passages = passagesByRisk.getOrDefault(finding.getRiskCode(), List.of());
                if (!passages.isEmpty()) {
                    LawPassage best = passages.get(0);
                    finding.setLegalBasis(best.title()
                            + (best.articleNumber() == null ? "" : " " + best.articleNumber()));
                }
            }
            LegalTraceEvent aiTrace = traceService.start(jobId, "AI_RISK_ENHANCEMENT",
                    details("baseRiskCount", baseFindings.size(),
                            "contextSnapshotId", contextSnapshot.getId(),
                            "contextHash", contextSnapshot.getContextHash(),
                            "supplementalLawPassageCount", passageCount(passagesByRisk)));
            List<RiskFinding> findings = enhancer.enhance(contract.getExtractedText(),
                    supplementalContext(job.getTenantId(), messages, passagesByRisk, product), baseFindings);
            traceService.success(aiTrace, details("baseRiskCount", baseFindings.size(),
                    "finalRiskCount", findings.size(),
                    "aiEnhancedCount", findings.stream().filter(RiskFinding::isAiEnhanced).count()));

            LegalTraceEvent persistTrace = traceService.start(jobId, "PERSIST_RISK_REPORT",
                    details("riskCount", findings.size()));
            for (RiskFinding finding : findings) {
                List<LawPassage> passages = passagesByRisk.getOrDefault(finding.getRiskCode(), List.of());
                LegalRiskItem item = toEntity(jobId, finding);
                riskMapper.insert(item);
                persistPrimaryEvidence(item.getId(), contract.getId(), finding);
                passages.forEach(passage -> persistLawEvidence(item.getId(), passage));
            }
            traceService.success(persistTrace, details("riskCount", findings.size(),
                    "lawEvidenceCount", passageCount(passagesByRisk)));
            String summary = findings.isEmpty()
                    ? "未命中当前规则库中的明显风险，仍建议签约前人工核对合同。"
                    : "共识别 " + findings.size() + " 项风险；结果为辅助信息，不构成法律意见。";
            updateStatus(jobId, LegalConstants.JOB_COMPLETED, summary, null, LocalDateTime.now());
            traceService.success(runTrace, details("finalRiskCount", findings.size(),
                    "contextSnapshotId", contextSnapshot.getId(),
                    "contextHash", contextSnapshot.getContextHash(),
                    "status", LegalConstants.JOB_COMPLETED));
            legalAgentService.notifyAssessmentCompleted(contract.getId(), findings);
        } catch (Exception ex) {
            log.error("Legal assessment failed, jobId={}", jobId, ex);
            updateStatus(jobId, LegalConstants.JOB_FAILED, null, safeMessage(ex), LocalDateTime.now());
            traceService.failure(runTrace, ex, details("status", LegalConstants.JOB_FAILED));
        }
    }

    private String legalSearchQuery(RiskFinding finding) {
        return switch (finding.getRiskCode()) {
            case "DEPOSIT_RETURN_UNCLEAR", "CHAT_DEPOSIT_FORFEITURE" ->
                    "住房租赁合同 押金 返还时间 扣减押金 情形 正当理由";
            case "LANDLORD_ENTRY_WITHOUT_CONSENT", "CHAT_UNANNOUNCED_ENTRY" ->
                    "出租人进入租赁住房 承租人占有使用 妨碍干扰 提前通知";
            case "UNILATERAL_TERMINATION" ->
                    "出租人单方解除租赁合同 承租人违约 催告 合理期限";
            case "LESSOR_AUTHORITY_MISSING" ->
                    "出租人身份 房屋权属 转租授权 核验";
            default -> finding.getTitle() + " " + finding.getConclusion();
        };
    }

    private String supplementalContext(Long tenantId, List<MessageVO> messages,
                                       Map<String, List<LawPassage>> passagesByRisk,
                                       HousePublishVO product) {
        StringBuilder result = new StringBuilder("[landlord_messages]\n");
        messages.stream().filter(message -> message.getFromId() != null && !message.getFromId().equals(tenantId))
                .limit(100).forEach(message -> result.append(message.getMessageId()).append(": ")
                        .append(message.getContent() == null ? "" : message.getContent()).append('\n'));
        result.append("[rental_product]\n");
        if (product != null) {
            result.append("publishId=").append(product.getPublishId()).append('\n');
            result.append("title=").append(product.getTitle()).append('\n');
            result.append("monthlyRent=").append(product.getMonthlyRent()).append('\n');
            result.append("depositMonths=").append(product.getDepositMonths()).append('\n');
            result.append("paymentCycleMonths=").append(product.getPaymentCycleMonths()).append('\n');
            result.append("propertyFee=").append(product.getPropertyFee()).append('\n');
            result.append("rentType=").append(product.getRentType()).append('\n');
            result.append("requirements=").append(product.getRentalRequirements()).append('\n');
            result.append("description=").append(product.getDescription()).append('\n');
            result.append("facilities=").append(product.getFacilities()).append('\n');
        }
        result.append("[retrieved_law]\n");
        passagesByRisk.values().stream().flatMap(List::stream).distinct().limit(20)
                .forEach(passage -> result.append(passage.title()).append(' ')
                        .append(passage.articleNumber() == null ? "" : passage.articleNumber()).append(": ")
                        .append(passage.text()).append('\n'));
        return result.substring(0, Math.min(result.length(), 20000));
    }

    private List<RiskFinding> deduplicate(List<RiskFinding> findings) {
        Map<String, RiskFinding> byCode = new LinkedHashMap<>();
        findings.forEach(finding -> byCode.putIfAbsent(finding.getRiskCode(), finding));
        return new ArrayList<>(byCode.values());
    }

    private void locateContractEvidence(LegalContract contract, List<LegalDocumentBlock> blocks, RiskFinding finding) {
        if (finding.getEvidenceQuote() == null || finding.getEvidenceQuote().isBlank()) {
            return;
        }
        blocks.stream().filter(block -> evidenceMatches(block.getBlockText(), finding.getEvidenceQuote()))
                .findFirst().ifPresent(block -> {
                    finding.setEvidenceType("CONTRACT");
                    finding.setEvidenceSourceId(String.valueOf(contract.getId()));
                    finding.setEvidencePageNumber(block.getPageNumber());
                    finding.setEvidenceParagraphIndex(block.getParagraphIndex());
                    finding.setEvidenceLocatorPrecision(block.getLocatorPrecision());
                });
    }

    private boolean evidenceMatches(String blockText, String evidenceQuote) {
        if (blockText == null || evidenceQuote == null) {
            return false;
        }
        String block = blockText.replaceAll("\\s+", "");
        String quote = evidenceQuote.replaceAll("\\s+", "");
        return !block.isEmpty() && !quote.isEmpty()
                && (block.contains(quote) || quote.contains(block));
    }

    private void persistPrimaryEvidence(Long riskItemId, Long contractId, RiskFinding finding) {
        if (finding.getEvidenceQuote() == null || finding.getEvidenceQuote().isBlank()) {
            return;
        }
        LegalEvidenceRef evidence = new LegalEvidenceRef();
        evidence.setRiskItemId(riskItemId);
        evidence.setEvidenceType(finding.getEvidenceType() == null ? "CONTRACT" : finding.getEvidenceType());
        evidence.setSourceId(finding.getEvidenceSourceId() == null
                ? String.valueOf(contractId) : finding.getEvidenceSourceId());
        evidence.setPageNumber(finding.getEvidencePageNumber());
        evidence.setParagraphIndex(finding.getEvidenceParagraphIndex());
        evidence.setLocatorPrecision(finding.getEvidenceLocatorPrecision());
        evidence.setQuoteText(finding.getEvidenceQuote());
        evidence.setCreatedAt(LocalDateTime.now());
        evidenceMapper.insert(evidence);
    }

    private void persistLawEvidence(Long riskItemId, LawPassage passage) {
        LegalEvidenceRef evidence = new LegalEvidenceRef();
        evidence.setRiskItemId(riskItemId);
        evidence.setEvidenceType("LAW");
        evidence.setSourceId(String.valueOf(passage.sourceId()));
        evidence.setClauseNumber(passage.articleNumber());
        evidence.setLocatorPrecision("ARTICLE_CHUNK");
        evidence.setQuoteText(passage.text().substring(0, Math.min(passage.text().length(), 1000)));
        evidence.setSourceUrl(passage.sourceUrl());
        evidence.setCreatedAt(LocalDateTime.now());
        evidenceMapper.insert(evidence);
    }

    private LegalRiskItem toEntity(Long jobId, RiskFinding finding) {
        LegalRiskItem item = new LegalRiskItem();
        item.setJobId(jobId);
        item.setRiskCode(finding.getRiskCode());
        item.setRiskLevel(finding.getLevel());
        item.setTitle(finding.getTitle());
        item.setConclusion(finding.getConclusion());
        item.setEvidenceQuote(finding.getEvidenceQuote());
        item.setLegalBasis(finding.getLegalBasis());
        item.setSuggestion(finding.getSuggestion());
        item.setConfidence(finding.getConfidence());
        item.setAiEnhanced(finding.isAiEnhanced());
        item.setCreatedAt(LocalDateTime.now());
        return item;
    }

    private void updateStatus(Long jobId, String status, String summary, String error, LocalDateTime completedAt) {
        LambdaUpdateWrapper<LegalAssessmentJob> update = new LambdaUpdateWrapper<LegalAssessmentJob>()
                .eq(LegalAssessmentJob::getId, jobId)
                .set(LegalAssessmentJob::getStatus, status)
                .set(LegalAssessmentJob::getUpdatedAt, LocalDateTime.now());
        if (summary != null) {
            update.set(LegalAssessmentJob::getSummary, summary);
        }
        if (error != null) {
            update.set(LegalAssessmentJob::getErrorMessage, error);
        }
        if (completedAt != null) {
            update.set(LegalAssessmentJob::getCompletedAt, completedAt);
        }
        jobMapper.update(null, update);
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "评估执行失败";
        }
        return message.substring(0, Math.min(message.length(), 500));
    }

    private List<String> riskCodes(List<RiskFinding> findings) {
        return findings.stream().map(RiskFinding::getRiskCode).toList();
    }

    private int passageCount(Map<String, List<LawPassage>> passagesByRisk) {
        return passagesByRisk.values().stream().mapToInt(List::size).sum();
    }

    private Map<String, Object> details(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i] != null && pairs[i + 1] != null) {
                result.put(String.valueOf(pairs[i]), pairs[i + 1]);
            }
        }
        return result;
    }
}
