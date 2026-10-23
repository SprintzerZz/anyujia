package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import java.util.List;

public interface LegalReranker {
    List<LawPassage> rerank(String query, List<LawPassage> candidates);

    boolean isAvailable();
}
