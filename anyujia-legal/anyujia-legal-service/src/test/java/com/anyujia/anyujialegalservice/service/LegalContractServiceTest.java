package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.anyujia.anyujialegalapi.domain.dto.ContractRegisterReqDTO;
import com.anyujia.anyujialegalservice.mapper.LegalContractMapper;
import com.anyujia.anyujialegalservice.mapper.LegalDocumentBlockMapper;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import org.junit.jupiter.api.Test;

class LegalContractServiceTest {
    @Test
    void rejectsRegistrationWhenTenantCannotAccessConversation() {
        LegalContractMapper mapper = mock(LegalContractMapper.class);
        ConversationAccessVerifier verifier = mock(ConversationAccessVerifier.class);
        LegalDocumentBlockMapper blockMapper = mock(LegalDocumentBlockMapper.class);
        LegalContractService service = new LegalContractService(mapper, blockMapper, verifier);
        ContractRegisterReqDTO request = request();
        doThrow(new ServiceException("forbidden")).when(verifier).requireTenantAccess(10L, 20L);

        assertThatThrownBy(() -> service.register(10L, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("forbidden");
        verifyNoInteractions(mapper);
        verifyNoInteractions(blockMapper);
    }

    private ContractRegisterReqDTO request() {
        ContractRegisterReqDTO request = new ContractRegisterReqDTO();
        request.setConversationId(20L);
        request.setObjectKey("contracts/test.pdf");
        request.setOriginalFilename("test.pdf");
        request.setSha256("a".repeat(64));
        request.setExtractedText("押金5000元");
        return request;
    }
}
