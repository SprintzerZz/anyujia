package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalapi.domain.vo.ContractVO;
import com.anyujia.anyujialegalservice.domain.model.ParsedDocument;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LegalDocumentIngestionService {
    private final LegalDocumentParsingService parsingService;
    private final LegalContractService contractService;
    private final ContractArchiveService archiveService;
    private final ConversationAccessVerifier accessVerifier;

    public ContractVO ingest(Long tenantId, Long conversationId, MultipartFile file) {
        if (file == null || file.isEmpty() || !StringUtils.hasText(file.getOriginalFilename())) {
            throw new ServiceException("合同文件不能为空", ResultCode.INVALID_PARA.getCode());
        }
        accessVerifier.requireTenantAccess(tenantId, conversationId);
        try {
            byte[] content = file.getBytes();
            ParsedDocument parsed = parsingService.parse(file.getOriginalFilename(), content);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            String objectKey = archiveService.archive(file);
            return contractService.registerParsedAfterAccessVerified(tenantId, conversationId, objectKey,
                    file.getOriginalFilename(), hash, parsed);
        } catch (IOException ex) {
            throw new ServiceException("读取合同文件失败", ResultCode.INVALID_PARA.getCode());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
