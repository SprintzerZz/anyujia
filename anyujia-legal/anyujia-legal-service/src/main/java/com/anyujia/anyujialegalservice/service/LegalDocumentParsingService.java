package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.config.LegalDocumentProperties;
import com.anyujia.anyujialegalservice.domain.model.ParsedDocument;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LegalDocumentParsingService {
    private final List<LegalDocumentParser> parsers;
    private final LegalDocumentProperties limits;

    public ParsedDocument parse(String filename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new ServiceException("合同文件不能为空", ResultCode.INVALID_PARA.getCode());
        }
        if (content.length > limits.getMaxBytes()) {
            throw new ServiceException("合同文件超过大小限制", ResultCode.INVALID_PARA.getCode());
        }
        return parsers.stream()
                .filter(parser -> parser.supports(filename, content))
                .findFirst()
                .orElseThrow(() -> new ServiceException("仅支持有效的 PDF 或 DOCX 文件",
                        ResultCode.INVALID_PARA.getCode()))
                .parse(content);
    }
}
