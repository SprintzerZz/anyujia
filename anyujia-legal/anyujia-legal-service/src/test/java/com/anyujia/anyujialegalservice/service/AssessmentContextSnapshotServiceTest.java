package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anyujia.anyujiachatapi.domain.vo.MessageVO;
import com.anyujia.anyujialegalservice.domain.entity.LegalAssessmentContextSnapshot;
import com.anyujia.anyujialegalservice.domain.entity.LegalAssessmentJob;
import com.anyujia.anyujialegalservice.domain.entity.LegalContract;
import com.anyujia.anyujialegalservice.domain.entity.LegalDocumentBlock;
import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import com.anyujia.anyujialegalservice.mapper.LegalAssessmentContextSnapshotMapper;
import com.anyujia.anyujiahouseapi.domain.vo.HousePublishVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssessmentContextSnapshotServiceTest {
    @Test
    void storesBoundedContextMetadataAndHashes() {
        LegalAssessmentContextSnapshotMapper mapper = mock(LegalAssessmentContextSnapshotMapper.class);
        when(mapper.insert(any(LegalAssessmentContextSnapshot.class))).thenAnswer(invocation -> {
            LegalAssessmentContextSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(123L);
            return 1;
        });
        AssessmentContextSnapshotService service = new AssessmentContextSnapshotService(mapper);

        LegalAssessmentContextSnapshot snapshot = service.createSnapshot(
                job(), contract(), List.of(block()), List.of(message()), product(),
                Map.of("DEPOSIT_RETURN_UNCLEAR", List.of(passage())));

        assertThat(snapshot.getId()).isEqualTo(123L);
        assertThat(snapshot.getContractBlockCount()).isEqualTo(1);
        assertThat(snapshot.getChatMessageCount()).isEqualTo(1);
        assertThat(snapshot.getChatMessageLimit()).isEqualTo(100);
        assertThat(snapshot.getHousePublishId()).isEqualTo(88L);
        assertThat(snapshot.getRetrievalQueryCount()).isEqualTo(1);
        assertThat(snapshot.getLawPassageCount()).isEqualTo(1);
        assertThat(snapshot.getContextHash()).hasSize(64);
        assertThat(snapshot.getContractHash()).hasSize(64);
        assertThat(snapshot.getChatHash()).hasSize(64);
        assertThat(snapshot.getProductHash()).hasSize(64);
        assertThat(snapshot.getRetrievalHash()).hasSize(64);

        ArgumentCaptor<LegalAssessmentContextSnapshot> captor =
                ArgumentCaptor.forClass(LegalAssessmentContextSnapshot.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getJobId()).isEqualTo(11L);
    }

    private LegalAssessmentJob job() {
        LegalAssessmentJob job = new LegalAssessmentJob();
        job.setId(11L);
        job.setTenantId(22L);
        job.setContractId(33L);
        job.setRegionCode("CN");
        job.setAsOfDate(LocalDate.of(2026, 10, 6));
        return job;
    }

    private LegalContract contract() {
        LegalContract contract = new LegalContract();
        contract.setId(33L);
        contract.setConversationId(44L);
        contract.setSha256("contract-sha");
        contract.setDocumentType("PDF");
        return contract;
    }

    private LegalDocumentBlock block() {
        LegalDocumentBlock block = new LegalDocumentBlock();
        block.setBlockIndex(1);
        block.setPageNumber(2);
        block.setParagraphIndex(3);
        block.setLocatorPrecision("PDF_PAGE_TEXT");
        return block;
    }

    private MessageVO message() {
        MessageVO message = new MessageVO();
        message.setMessageId("m-1");
        message.setFromId(99L);
        message.setContent("押金不能退");
        return message;
    }

    private HousePublishVO product() {
        HousePublishVO product = new HousePublishVO();
        product.setPublishId(88L);
        product.setTitle("整租一居室");
        product.setMonthlyRent(BigDecimal.valueOf(2500));
        product.setDepositMonths(2);
        product.setPaymentCycleMonths(3);
        product.setPropertyFee(BigDecimal.valueOf(120));
        product.setRentalRequirements("禁止养宠物");
        product.setDescription("近地铁");
        product.setFacilities(List.of("空调", "洗衣机"));
        return product;
    }

    private LawPassage passage() {
        return new LawPassage(1L, 2L, "民法典", "第七百零三条",
                "租赁合同是出租人将租赁物交付承租人使用、收益，承租人支付租金的合同。",
                "https://example.test/law", 1.0);
    }
}
