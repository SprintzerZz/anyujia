package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalapi.domain.dto.LegalAgentBindReqDTO;
import com.anyujia.anyujialegalservice.domain.entity.LegalAgentConversation;
import com.anyujia.anyujialegalservice.domain.entity.LegalContract;
import com.anyujia.anyujialegalservice.mapper.LegalAgentConversationMapper;
import com.anyujia.anyujialegalservice.mapper.LegalContractMapper;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LegalAgentConversationService {
    private final LegalAgentConversationMapper mapper;
    private final LegalContractMapper contractMapper;

    @Transactional(rollbackFor = Exception.class)
    public void bind(LegalAgentBindReqDTO request) {
        LegalContract contract = contractMapper.selectOne(new LambdaQueryWrapper<LegalContract>()
                .eq(LegalContract::getId, request.getContractId())
                .eq(LegalContract::getTenantId, request.getTenantId())
                .eq(LegalContract::getConversationId, request.getLandlordConversationId()));
        if (contract == null) {
            throw new ServiceException("合同不存在、租户不匹配或会话不匹配",
                    ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
        LegalAgentConversation existing = findByChatConversationId(request.getChatConversationId());
        if (existing != null) {
            if (!request.getTenantId().equals(existing.getTenantId())
                    || !request.getLandlordConversationId().equals(existing.getLandlordConversationId())
                    || !request.getContractId().equals(existing.getContractId())) {
                throw new ServiceException("法律助手会话已绑定到其他合同或租户",
                        ResultCode.INVALID_PARA.getCode());
            }
            return;
        }
        LegalAgentConversation entity = new LegalAgentConversation();
        entity.setTenantId(request.getTenantId());
        entity.setChatConversationId(request.getChatConversationId());
        entity.setLandlordConversationId(request.getLandlordConversationId());
        entity.setContractId(request.getContractId());
        entity.setStatus(1);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        mapper.insert(entity);
    }

    public LegalAgentConversation findByChatConversationId(Long chatConversationId) {
        return mapper.selectOne(new LambdaQueryWrapper<LegalAgentConversation>()
                .eq(LegalAgentConversation::getChatConversationId, chatConversationId)
                .eq(LegalAgentConversation::getStatus, 1)
                .last("LIMIT 1"));
    }

    public LegalAgentConversation findLatestByContractId(Long contractId) {
        return mapper.selectOne(new LambdaQueryWrapper<LegalAgentConversation>()
                .eq(LegalAgentConversation::getContractId, contractId)
                .orderByDesc(LegalAgentConversation::getId)
                .last("LIMIT 1"));
    }
}
