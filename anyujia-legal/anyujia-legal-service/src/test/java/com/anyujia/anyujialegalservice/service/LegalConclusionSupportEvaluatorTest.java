package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalapi.domain.vo.LegalCitationVO;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegalConclusionSupportEvaluatorTest {
    @Test
    void evaluatesEachConclusionAgainstOnlyAllowListedCitations() {
        LegalCitationVO citation = LegalCitationVO.builder()
                .index(1).title("民法典").quote("押金应当按照约定返还，不得无依据扣除").build();

        var result = new LegalConclusionSupportEvaluator().evaluate(
                "押金应当按照约定返还，不得无依据扣除。房东可以任意收取三倍家具折旧费。",
                List.of(citation));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSupportLevel()).isEqualTo("SUPPORTED");
        assertThat(result.get(0).getCitationIndexes()).containsExactly(1);
        assertThat(result.get(1).getSupportLevel()).isEqualTo("UNSUPPORTED");
        assertThat(result.get(1).getCitationIndexes()).isEmpty();
    }
}
