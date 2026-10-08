package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anyujia.anyujialegalservice.config.LegalDocumentProperties;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegalDocumentParsingServiceTest {
    @Test
    void rejectsUnknownFileSignature() {
        LegalDocumentParsingService service = new LegalDocumentParsingService(List.of(), new LegalDocumentProperties());

        assertThatThrownBy(() -> service.parse("contract.pdf", "not-a-pdf".getBytes()))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void enforcesConfiguredByteLimitBeforeParsing() {
        LegalDocumentProperties limits = new LegalDocumentProperties();
        limits.setMaxBytes(4);
        LegalDocumentParsingService service = new LegalDocumentParsingService(List.of(), limits);

        assertThatThrownBy(() -> service.parse("contract.pdf", new byte[5]))
                .isInstanceOf(ServiceException.class);
    }
}
