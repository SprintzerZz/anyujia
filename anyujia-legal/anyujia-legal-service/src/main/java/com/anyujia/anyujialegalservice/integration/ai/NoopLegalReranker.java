package com.anyujia.anyujialegalservice.integration.ai;

import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import com.anyujia.anyujialegalservice.service.LegalReranker;
import java.util.List;

public class NoopLegalReranker implements LegalReranker {
    @Override
    public List<LawPassage> rerank(String query, List<LawPassage> candidates) {
        return candidates;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
