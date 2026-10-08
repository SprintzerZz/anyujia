package com.anyujia.anyujialegalservice.integration.ai;

import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import com.anyujia.anyujialegalservice.domain.model.LegalAnswerGeneration;
import com.anyujia.anyujialegalservice.service.LegalAnswerGenerator;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "legal.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopLegalAnswerGenerator implements LegalAnswerGenerator {
    @Override
    public LegalAnswerGeneration generate(String query, List<LawPassage> passages) {
        return ExtractiveLegalAnswer.compose(passages, "EXTRACTIVE_FALLBACK");
    }
}
