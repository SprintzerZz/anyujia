package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujiahouseapi.domain.vo.HousePublishVO;
import java.util.Optional;

public interface RentalProductEvidenceProvider {
    Optional<HousePublishVO> snapshot(Long tenantId, Long conversationId);
}
