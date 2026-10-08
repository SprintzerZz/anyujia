package com.anyujia.anyujialegalservice.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoopLegalAnswerGeneratorTest {
    @Test
    void createsGroundedExtractiveFallbackWithoutAnAiCall() {
        NoopLegalAnswerGenerator generator = new NoopLegalAnswerGenerator();
        var result = generator.generate("谁负责维修？", List.of(
                new LawPassage(1L, 2L, "中华人民共和国民法典", "第七百一十三条",
                        "出租人未履行维修义务的，承租人可以自行维修。", "https://gov.cn/law", 0.9)));

        assertThat(result.answer()).contains("第七百一十三条").contains("自行维修");
        assertThat(result.citationIndexes()).containsExactly(1);
        assertThat(result.mode()).isEqualTo("EXTRACTIVE_FALLBACK");
        assertThat(result.aiGenerated()).isFalse();
        assertThat(result.degraded()).isTrue();
    }
}
