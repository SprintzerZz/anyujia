package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.domain.entity.LegalDocumentBlock;
import com.anyujia.anyujialegalservice.mapper.LegalDocumentBlockMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegalContractContextCompressorTest {
    @Test
    void selectsRiskRelevantBlocksAndPreservesEvidenceLocator() {
        LegalDocumentBlockMapper mapper = mock(LegalDocumentBlockMapper.class);
        LegalDocumentBlock deposit = block(2, 3, "押金条款", "押金为两个月租金，退租后七日内退还。");
        LegalDocumentBlock repair = block(1, 2, "维修条款", "房屋维修由出租人承担。");
        when(mapper.selectList(any())).thenReturn(List.of(repair, deposit));

        String context = new LegalContractContextCompressor(mapper)
                .build(10L, "fallback", "押金什么时候退还");

        assertThat(context).contains("index=2", "page=3", "押金条款", "押金为两个月租金");
        assertThat(context).doesNotContain("fallback");
    }

    @Test
    void fallsBackWithoutBlocksAndMarksTruncation() {
        LegalDocumentBlockMapper mapper = mock(LegalDocumentBlockMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());

        String context = new LegalContractContextCompressor(mapper)
                .build(10L, "abcdefghijklmnopqrstuvwxyz", "", 10);

        assertThat(context).contains("[contract_context_truncated]");
    }

    private LegalDocumentBlock block(int index, int page, String heading, String text) {
        LegalDocumentBlock block = new LegalDocumentBlock();
        block.setId((long) index);
        block.setContractId(10L);
        block.setBlockIndex(index);
        block.setPageNumber(page);
        block.setHeading(heading);
        block.setBlockText(text);
        return block;
    }
}
