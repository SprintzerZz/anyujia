package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anyujia.anyujialegalservice.domain.entity.LegalTraceEvent;
import com.anyujia.anyujialegalservice.mapper.LegalTraceEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LegalTraceServiceTest {
    @Test
    void recordsSuccessSpanWithDurationAndJsonDetails() {
        LegalTraceEventMapper mapper = mock(LegalTraceEventMapper.class);
        when(mapper.insert(any(LegalTraceEvent.class))).thenAnswer(invocation -> {
            LegalTraceEvent event = invocation.getArgument(0);
            event.setId(10L);
            return 1;
        });
        LegalTraceService service = new LegalTraceService(mapper, new ObjectMapper());

        LegalTraceEvent event = service.start(99L, "LEGAL_RETRIEVAL", Map.of("queryCount", 2));
        service.success(event, Map.of("passageCount", 3));

        ArgumentCaptor<LegalTraceEvent> captor = ArgumentCaptor.forClass(LegalTraceEvent.class);
        verify(mapper).updateById(captor.capture());
        LegalTraceEvent saved = captor.getValue();
        assertThat(saved.getJobId()).isEqualTo(99L);
        assertThat(saved.getStage()).isEqualTo("LEGAL_RETRIEVAL");
        assertThat(saved.getStatus()).isEqualTo("SUCCESS");
        assertThat(saved.getDurationMillis()).isNotNegative();
        assertThat(saved.getDetailsJson()).contains("\"passageCount\":3");
    }

    @Test
    void traceStorageFailureDoesNotEscape() {
        LegalTraceEventMapper mapper = mock(LegalTraceEventMapper.class);
        doThrow(new IllegalStateException("database unavailable")).when(mapper).insert(any(LegalTraceEvent.class));
        LegalTraceService service = new LegalTraceService(mapper, new ObjectMapper());

        assertThatCode(() -> service.start(99L, "CONTRACT_CONTEXT", Map.of("contractId", 1L)))
                .doesNotThrowAnyException();
    }
}
