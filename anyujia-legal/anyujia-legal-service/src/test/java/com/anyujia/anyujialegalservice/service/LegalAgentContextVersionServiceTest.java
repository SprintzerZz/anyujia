package com.anyujia.anyujialegalservice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegalAgentContextVersionServiceTest {
    @Test
    void createsStableSha256Fingerprint() {
        LegalAgentContextVersionService service = new LegalAgentContextVersionService(null);

        String first = service.sha256("contract|chat|law");
        String second = service.sha256("contract|chat|law");

        assertThat(first).hasSize(64).isEqualTo(second);
        assertThat(first).isNotEqualTo(service.sha256("contract|changed|law"));
    }
}
