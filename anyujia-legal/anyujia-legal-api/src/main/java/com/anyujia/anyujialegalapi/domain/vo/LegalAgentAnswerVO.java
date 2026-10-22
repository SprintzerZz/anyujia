package com.anyujia.anyujialegalapi.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class LegalAgentAnswerVO {
    private String answerText;
    private List<LegalCitationVO> citations;
    private String mode;
    private boolean degraded;
    private String contextHash;
    private String contextStatus;
}
