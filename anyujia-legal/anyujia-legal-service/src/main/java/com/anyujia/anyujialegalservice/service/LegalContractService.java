package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalapi.domain.dto.ContractRegisterReqDTO;
import com.anyujia.anyujialegalapi.domain.vo.ContractVO;
import com.anyujia.anyujialegalservice.constants.LegalConstants;
import com.anyujia.anyujialegalservice.domain.entity.LegalContract;
import com.anyujia.anyujialegalservice.domain.entity.LegalDocumentBlock;
import com.anyujia.anyujialegalservice.domain.model.ParsedBlock;
import com.anyujia.anyujialegalservice.domain.model.ParsedDocument;
import com.anyujia.anyujialegalservice.mapper.LegalContractMapper;
import com.anyujia.anyujialegalservice.mapper.LegalDocumentBlockMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LegalContractService {
    private final LegalContractMapper contractMapper;
    private final LegalDocumentBlockMapper blockMapper;
    private final ConversationAccessVerifier accessVerifier;

    @Transactional(rollbackFor = Exception.class)
    public ContractVO register(Long tenantId, ContractRegisterReqDTO request) {
        ParsedBlock block = new ParsedBlock(0, 1, 0, "UNPAGED_TEXT", null,
                request.getExtractedText(), 0, request.getExtractedText().length());
        ParsedDocument parsed = new ParsedDocument("TEXT", 1, "UNPAGED_TEXT",
                request.getExtractedText(), List.of(block));
        return registerParsed(tenantId, request.getConversationId(), request.getObjectKey(),
                request.getOriginalFilename(), request.getSha256(), parsed);
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractVO registerParsed(Long tenantId, Long conversationId, String objectKey,
                                     String originalFilename, String sha256, ParsedDocument parsed) {
        accessVerifier.requireTenantAccess(tenantId, conversationId);
        return registerParsedAfterAccessVerified(tenantId, conversationId, objectKey,
                originalFilename, sha256, parsed);
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractVO registerParsedAfterAccessVerified(Long tenantId, Long conversationId, String objectKey,
                                                        String originalFilename, String sha256,
                                                        ParsedDocument parsed) {
        LegalContract existing = findExisting(tenantId, conversationId, sha256);
        if (existing != null) {
            return toVO(existing);
        }
        LocalDateTime now = LocalDateTime.now();
        LegalContract contract = new LegalContract();
        contract.setTenantId(tenantId);
        contract.setConversationId(conversationId);
        contract.setObjectKey(objectKey);
        contract.setOriginalFilename(originalFilename);
        contract.setSha256(sha256.toLowerCase());
        contract.setExtractedText(parsed.fullText());
        contract.setDocumentType(parsed.documentType());
        contract.setPageCount(parsed.pageCount());
        contract.setLocatorPrecision(parsed.locatorPrecision());
        contract.setStatus(LegalConstants.CONTRACT_READY);
        contract.setCreatedAt(now);
        contract.setUpdatedAt(now);
        try {
            contractMapper.insert(contract);
        } catch (DuplicateKeyException ex) {
            return toVO(findExisting(tenantId, conversationId, sha256));
        }
        for (ParsedBlock block : parsed.blocks()) {
            blockMapper.insert(toEntity(contract.getId(), block));
        }
        return toVO(contract);
    }

    private LegalDocumentBlock toEntity(Long contractId, ParsedBlock block) {
        LegalDocumentBlock entity = new LegalDocumentBlock();
        entity.setContractId(contractId);
        entity.setBlockIndex(block.blockIndex());
        entity.setPageNumber(block.pageNumber());
        entity.setParagraphIndex(block.paragraphIndex());
        entity.setLocatorPrecision(block.locatorPrecision());
        entity.setHeading(block.heading());
        entity.setBlockText(block.text());
        entity.setCharStart(block.charStart());
        entity.setCharEnd(block.charEnd());
        return entity;
    }

    private LegalContract findExisting(Long tenantId, Long conversationId, String sha256) {
        return contractMapper.selectOne(new LambdaQueryWrapper<LegalContract>()
                .eq(LegalContract::getTenantId, tenantId)
                .eq(LegalContract::getConversationId, conversationId)
                .eq(LegalContract::getSha256, sha256.toLowerCase()));
    }

    private ContractVO toVO(LegalContract contract) {
        ContractVO result = new ContractVO();
        result.setContractId(contract.getId());
        result.setConversationId(contract.getConversationId());
        result.setOriginalFilename(contract.getOriginalFilename());
        result.setSha256(contract.getSha256());
        result.setDocumentType(contract.getDocumentType());
        result.setPageCount(contract.getPageCount());
        result.setLocatorPrecision(contract.getLocatorPrecision());
        result.setStatus(contract.getStatus());
        result.setCreatedAt(toEpochMilli(contract.getCreatedAt()));
        return result;
    }

    private long toEpochMilli(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
