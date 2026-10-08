package com.anyujia.anyujiachatservice.service;

import com.anyujia.anyujiachatapi.domain.dto.ConversationCreateReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.ConversationContextReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.ConversationGetReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.ConversationHouseReqDTO;
import com.anyujia.anyujiachatapi.domain.dto.ConversationListReqDTO;
import com.anyujia.anyujiachatapi.domain.vo.ConversationCreateVO;
import com.anyujia.anyujiachatapi.domain.vo.ConversationContextVO;
import com.anyujia.anyujiachatapi.domain.vo.ConversationVO;
import java.util.List;

public interface ConversationService {
    ConversationCreateVO create(Long userId, Integer userType, ConversationCreateReqDTO request);
    ConversationCreateVO get(Long userId, Integer userType, ConversationGetReqDTO request);
    List<ConversationVO> list(Long userId, ConversationListReqDTO request);
    boolean hasHouse(Long userId, ConversationHouseReqDTO request);
    ConversationContextVO context(ConversationContextReqDTO request);
}
