package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.domain.model.LawPassage;
import com.anyujia.anyujialegalservice.domain.model.LegalAnswerGeneration;
import java.util.List;

public interface LegalAnswerGenerator {
    LegalAnswerGeneration generate(String query, List<LawPassage> passages);
}
