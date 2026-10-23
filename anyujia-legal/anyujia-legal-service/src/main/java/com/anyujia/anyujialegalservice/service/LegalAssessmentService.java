package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalapi.domain.dto.AssessmentStartReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.AssessmentJobVO;
import com.anyujia.anyujialegalapi.domain.vo.EvidenceRefVO;
import com.anyujia.anyujialegalapi.domain.vo.RiskItemVO;
import com.anyujia.anyujialegalservice.constants.LegalConstants;
import com.anyujia.anyujialegalservice.domain.entity.LegalAssessmentJob;
import com.anyujia.anyujialegalservice.domain.entity.LegalContract;
import com.anyujia.anyujialegalservice.domain.entity.LegalEvidenceRef;
import com.anyujia.anyujialegalservice.domain.entity.LegalRiskItem;
import com.anyujia.anyujialegalservice.mapper.LegalAssessmentJobMapper;
import com.anyujia.anyujialegalservice.mapper.LegalContractMapper;
import com.anyujia.anyujialegalservice.mapper.LegalEvidenceRefMapper;
import com.anyujia.anyujialegalservice.mapper.LegalRiskItemMapper;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class LegalAssessmentService {
    private final LegalContractMapper contractMapper;
    private final LegalAssessmentJobMapper jobMapper;
    private final LegalRiskItemMapper riskMapper;
    private final LegalEvidenceRefMapper evidenceMapper;
    private final AssessmentTaskDispatcher taskDispatcher;

    @Transactional(rollbackFor = Exception.class)
    public AssessmentJobVO start(Long tenantId, AssessmentStartReqDTO request) {
        LegalContract contract = requireOwnedContract(tenantId, request.getContractId());
        LegalAssessmentJob existing = findByRequestId(tenantId, request.getRequestId());
        if (existing != null) {
            if (!existing.getContractId().equals(contract.getId())) {
                throw new ServiceException("requestId 已用于其他合同", ResultCode.INVALID_PARA.getCode());
            }
            return toVO(existing, List.of());
        }
        LocalDateTime now = LocalDateTime.now();
        LegalAssessmentJob job = new LegalAssessmentJob();
        job.setRequestId(request.getRequestId());
        job.setContractId(contract.getId());
        job.setTenantId(tenantId);
        job.setRegionCode(request.getRegionCode() == null || request.getRegionCode().isBlank()
                ? "GLOBAL" : request.getRegionCode().toUpperCase());
        job.setAsOfDate(request.getAsOfDate() == null || request.getAsOfDate().isBlank()
                ? LocalDate.now() : LocalDate.parse(request.getAsOfDate()));
        job.setStatus(LegalConstants.JOB_PENDING);
        job.setAttemptCount(0);
        job.setNextRunAt(now);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        try {
            jobMapper.insert(job);
        } catch (DuplicateKeyException ex) {
            return toVO(findByRequestId(tenantId, request.getRequestId()), List.of());
        }
        dispatchAfterCommit(job.getId());
        return toVO(job, List.of());
    }

    public AssessmentJobVO get(Long tenantId, Long jobId) {
        LegalAssessmentJob job = jobMapper.selectOne(new LambdaQueryWrapper<LegalAssessmentJob>()
                .eq(LegalAssessmentJob::getId, jobId)
                .eq(LegalAssessmentJob::getTenantId, tenantId));
        if (job == null) {
            throw new ServiceException("风险评估任务不存在或无权访问", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
        List<LegalRiskItem> risks = riskMapper.selectList(new LambdaQueryWrapper<LegalRiskItem>()
                .eq(LegalRiskItem::getJobId, jobId)
                .orderByAsc(LegalRiskItem::getId));
        List<Long> riskIds = risks.stream().map(LegalRiskItem::getId).toList();
        Map<Long, List<LegalEvidenceRef>> evidenceByRisk = riskIds.isEmpty() ? Map.of()
                : evidenceMapper.selectList(new LambdaQueryWrapper<LegalEvidenceRef>()
                        .in(LegalEvidenceRef::getRiskItemId, riskIds)
                        .orderByAsc(LegalEvidenceRef::getId)).stream()
                .collect(Collectors.groupingBy(LegalEvidenceRef::getRiskItemId));
        return toVO(job, risks, evidenceByRisk);
    }

    private LegalContract requireOwnedContract(Long tenantId, Long contractId) {
        LegalContract contract = contractMapper.selectOne(new LambdaQueryWrapper<LegalContract>()
                .eq(LegalContract::getId, contractId)
                .eq(LegalContract::getTenantId, tenantId));
        if (contract == null) {
            throw new ServiceException("合同不存在或无权访问", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
        return contract;
    }

    private LegalAssessmentJob findByRequestId(Long tenantId, String requestId) {
        return jobMapper.selectOne(new LambdaQueryWrapper<LegalAssessmentJob>()
                .eq(LegalAssessmentJob::getTenantId, tenantId)
                .eq(LegalAssessmentJob::getRequestId, requestId));
    }

    private void dispatchAfterCommit(Long jobId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            taskDispatcher.submit(jobId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskDispatcher.submit(jobId);
            }
        });
    }

    private AssessmentJobVO toVO(LegalAssessmentJob job, List<LegalRiskItem> risks) {
        return toVO(job, risks, Map.of());
    }

    private AssessmentJobVO toVO(LegalAssessmentJob job, List<LegalRiskItem> risks,
                                 Map<Long, List<LegalEvidenceRef>> evidenceByRisk) {
        AssessmentJobVO result = new AssessmentJobVO();
        result.setJobId(job.getId());
        result.setContractId(job.getContractId());
        result.setStatus(job.getStatus());
        result.setSummary(job.getSummary());
        result.setErrorMessage(job.getErrorMessage());
        result.setCreatedAt(toEpochMilli(job.getCreatedAt()));
        result.setCompletedAt(toEpochMilli(job.getCompletedAt()));
        result.setRisks(risks.stream().map(risk -> toRiskVO(risk,
                evidenceByRisk.getOrDefault(risk.getId(), List.of()))).toList());
        return result;
    }

    private RiskItemVO toRiskVO(LegalRiskItem risk, List<LegalEvidenceRef> evidence) {
        RiskItemVO result = new RiskItemVO();
        result.setRiskCode(risk.getRiskCode());
        result.setLevel(risk.getRiskLevel());
        result.setTitle(risk.getTitle());
        result.setConclusion(risk.getConclusion());
        result.setEvidenceQuote(risk.getEvidenceQuote());
        result.setLegalBasis(risk.getLegalBasis());
        result.setSuggestion(risk.getSuggestion());
        result.setConfidence(risk.getConfidence());
        result.setAiEnhanced(risk.getAiEnhanced());
        result.setEvidence(evidence.stream().map(this::toEvidenceVO).toList());
        return result;
    }

    private EvidenceRefVO toEvidenceVO(LegalEvidenceRef evidence) {
        EvidenceRefVO result = new EvidenceRefVO();
        result.setEvidenceType(evidence.getEvidenceType());
        result.setSourceId(evidence.getSourceId());
        result.setPageNumber(evidence.getPageNumber());
        result.setParagraphIndex(evidence.getParagraphIndex());
        result.setClauseNumber(evidence.getClauseNumber());
        result.setLocatorPrecision(evidence.getLocatorPrecision());
        result.setQuoteText(evidence.getQuoteText());
        result.setSourceUrl(evidence.getSourceUrl());
        return result;
    }

    private Long toEpochMilli(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
