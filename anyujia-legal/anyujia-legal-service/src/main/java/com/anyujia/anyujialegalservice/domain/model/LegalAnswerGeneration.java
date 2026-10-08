package com.anyujia.anyujialegalservice.domain.model;

import java.util.List;

public record LegalAnswerGeneration(String answer, List<Integer> citationIndexes,
                                    String mode, boolean aiGenerated, boolean degraded) {
}
