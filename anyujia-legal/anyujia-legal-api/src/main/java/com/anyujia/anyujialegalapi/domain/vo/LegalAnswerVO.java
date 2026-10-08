package com.anyujia.anyujialegalapi.domain.vo;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LegalAnswerVO {
    String answer;
    List<LegalCitationVO> citations;
    String mode;
    boolean aiGenerated;
    boolean degraded;
    Long retrievalMillis;
    Long generationMillis;
    Long elapsedMillis;
    String disclaimer;
}
