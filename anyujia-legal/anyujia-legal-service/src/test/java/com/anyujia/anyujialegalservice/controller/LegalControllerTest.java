package com.anyujia.anyujialegalservice.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anyujia.anyujialegalapi.domain.dto.LegalSearchReqDTO;
import com.anyujia.anyujialegalapi.domain.dto.LegalAnswerReqDTO;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import org.junit.jupiter.api.Test;

class LegalControllerTest {
    @Test
    void rejectsTenantAccessToKnowledgeSearchDiagnostics() {
        LegalController controller = new LegalController(null, null, null, null, null, null, null, null, null, null);
        LegalSearchReqDTO request = new LegalSearchReqDTO();
        request.setQuery("押金退还");

        assertThatThrownBy(() -> controller.searchKnowledge("app", request))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void rejectsTenantAccessToKnowledgeAnswerDiagnostics() {
        LegalController controller = new LegalController(null, null, null, null, null, null, null, null, null, null);
        LegalAnswerReqDTO request = new LegalAnswerReqDTO();
        request.setQuery("押金能否扣除？");

        assertThatThrownBy(() -> controller.answerKnowledge("app", request))
                .isInstanceOf(ServiceException.class);
    }
}
