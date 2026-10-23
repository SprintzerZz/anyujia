package com.anyujia.anyujialegalservice.service;

import java.util.Optional;

public interface LegalEntailmentScorer {
    Optional<Result> score(String premise, String hypothesis);

    boolean isAvailable();

    record Result(String label, double score) {
    }
}
