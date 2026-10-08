package com.anyujia.anyujialegalapi.domain.vo;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LegalSearchVO {
    Long elapsedMillis;
    List<LegalPassageVO> passages;
}
