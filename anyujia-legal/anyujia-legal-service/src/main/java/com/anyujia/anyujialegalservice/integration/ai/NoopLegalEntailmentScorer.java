package com.anyujia.anyujialegalservice.integration.ai;

import com.anyujia.anyujialegalservice.service.LegalEntailmentScorer;
import java.util.Optional;

public class NoopLegalEntailmentScorer implements LegalEntailmentScorer {
    @Override
    public Optional<Result> score(String premise, String hypothesis) {
        return Optional.empty();
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
