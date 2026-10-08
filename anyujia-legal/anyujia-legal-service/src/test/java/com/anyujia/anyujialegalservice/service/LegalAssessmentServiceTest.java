package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anyujia.anyujialegalapi.domain.dto.AssessmentStartReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.AssessmentJobVO;
import com.anyujia.anyujialegalservice.constants.LegalConstants;
import com.anyujia.anyujialegalservice.domain.entity.LegalAssessmentJob;
import com.anyujia.anyujialegalservice.domain.entity.LegalContract;
import com.anyujia.anyujialegalservice.domain.entity.LegalEvidenceRef;
import com.anyujia.anyujialegalservice.domain.entity.LegalRiskItem;
import com.anyujia.anyujialegalservice.mapper.LegalAssessmentJobMapper;
import com.anyujia.anyujialegalservice.mapper.LegalContractMapper;
import com.anyujia.anyujialegalservice.mapper.LegalEvidenceRefMapper;
import com.anyujia.anyujialegalservice.mapper.LegalRiskItemMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegalAssessmentServiceTest {
    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), "test");
        TableInfoHelper.initTableInfo(assistant, LegalContract.class);
        TableInfoHelper.initTableInfo(assistant, LegalAssessmentJob.class);
        TableInfoHelper.initTableInfo(assistant, LegalEvidenceRef.class);
    }

    @Test
    void returnsExistingJobForSameRequestWithoutDispatchingAgain() {
        LegalContractMapper contractMapper = mock(LegalContractMapper.class);
        LegalAssessmentJobMapper jobMapper = mock(LegalAssessmentJobMapper.class);
        LegalRiskItemMapper riskMapper = mock(LegalRiskItemMapper.class);
        AssessmentTaskDispatcher dispatcher = mock(AssessmentTaskDispatcher.class);
        LegalEvidenceRefMapper evidenceMapper = mock(LegalEvidenceRefMapper.class);
        LegalAssessmentService service = new LegalAssessmentService(
                contractMapper, jobMapper, riskMapper, evidenceMapper, dispatcher);
        LegalContract contract = contract(100L, 7L);
        LegalAssessmentJob existing = job(200L, 100L, 7L, "req-1");
        when(contractMapper.selectOne(any())).thenReturn(contract);
        when(jobMapper.selectOne(any())).thenReturn(existing);

        AssessmentJobVO result = service.start(7L, startRequest(100L, "req-1"));

        assertThat(result.getJobId()).isEqualTo(200L);
        assertThat(result.getStatus()).isEqualTo(LegalConstants.JOB_PENDING);
        verify(jobMapper, never()).insert(any(LegalAssessmentJob.class));
        verify(dispatcher, never()).submit(any());
    }

    @Test
    void createsAndDispatchesNewJob() {
        LegalContractMapper contractMapper = mock(LegalContractMapper.class);
        LegalAssessmentJobMapper jobMapper = mock(LegalAssessmentJobMapper.class);
        LegalRiskItemMapper riskMapper = mock(LegalRiskItemMapper.class);
        AssessmentTaskDispatcher dispatcher = mock(AssessmentTaskDispatcher.class);
        LegalEvidenceRefMapper evidenceMapper = mock(LegalEvidenceRefMapper.class);
        LegalAssessmentService service = new LegalAssessmentService(
                contractMapper, jobMapper, riskMapper, evidenceMapper, dispatcher);
        when(contractMapper.selectOne(any())).thenReturn(contract(100L, 7L));
        when(jobMapper.selectOne(any())).thenReturn(null);
        when(jobMapper.insert(any(LegalAssessmentJob.class))).thenAnswer(invocation -> {
            LegalAssessmentJob value = invocation.getArgument(0);
            value.setId(201L);
            return 1;
        });

        AssessmentJobVO result = service.start(7L, startRequest(100L, "req-2"));

        assertThat(result.getJobId()).isEqualTo(201L);
        verify(dispatcher).submit(201L);
    }

    @Test
    void mapsStructuredEvidenceIntoReport() {
        LegalContractMapper contractMapper = mock(LegalContractMapper.class);
        LegalAssessmentJobMapper jobMapper = mock(LegalAssessmentJobMapper.class);
        LegalRiskItemMapper riskMapper = mock(LegalRiskItemMapper.class);
        LegalEvidenceRefMapper evidenceMapper = mock(LegalEvidenceRefMapper.class);
        LegalAssessmentService service = new LegalAssessmentService(
                contractMapper, jobMapper, riskMapper, evidenceMapper, mock(AssessmentTaskDispatcher.class));
        when(jobMapper.selectOne(any())).thenReturn(job(200L, 100L, 7L, "req-1"));
        LegalRiskItem risk = new LegalRiskItem();
        risk.setId(300L);
        risk.setRiskCode("R1");
        risk.setRiskLevel("HIGH");
        when(riskMapper.selectList(any())).thenReturn(List.of(risk));
        LegalEvidenceRef evidence = new LegalEvidenceRef();
        evidence.setRiskItemId(300L);
        evidence.setEvidenceType("CONTRACT");
        evidence.setPageNumber(2);
        evidence.setParagraphIndex(8);
        evidence.setLocatorPrecision("EXACT_PAGE");
        when(evidenceMapper.selectList(any())).thenReturn(List.of(evidence));

        AssessmentJobVO result = service.get(7L, 200L);

        assertThat(result.getRisks().get(0).getEvidence()).singleElement().satisfies(item -> {
            assertThat(item.getPageNumber()).isEqualTo(2);
            assertThat(item.getParagraphIndex()).isEqualTo(8);
            assertThat(item.getLocatorPrecision()).isEqualTo("EXACT_PAGE");
        });
    }

    private LegalContract contract(Long id, Long tenantId) {
        LegalContract contract = new LegalContract();
        contract.setId(id);
        contract.setTenantId(tenantId);
        return contract;
    }

    private LegalAssessmentJob job(Long id, Long contractId, Long tenantId, String requestId) {
        LegalAssessmentJob job = new LegalAssessmentJob();
        job.setId(id);
        job.setContractId(contractId);
        job.setTenantId(tenantId);
        job.setRequestId(requestId);
        job.setStatus(LegalConstants.JOB_PENDING);
        job.setCreatedAt(LocalDateTime.now());
        return job;
    }

    private AssessmentStartReqDTO startRequest(Long contractId, String requestId) {
        AssessmentStartReqDTO request = new AssessmentStartReqDTO();
        request.setContractId(contractId);
        request.setRequestId(requestId);
        return request;
    }
}
