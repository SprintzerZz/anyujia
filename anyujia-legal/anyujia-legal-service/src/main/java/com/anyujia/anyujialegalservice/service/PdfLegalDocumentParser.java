package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.config.LegalDocumentProperties;
import com.anyujia.anyujialegalservice.domain.model.ParsedBlock;
import com.anyujia.anyujialegalservice.domain.model.ParsedDocument;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PdfLegalDocumentParser implements LegalDocumentParser {
    private final LegalDocumentProperties limits;

    @Override
    public boolean supports(String filename, byte[] content) {
        return content.length >= 5 && content[0] == '%' && content[1] == 'P'
                && content[2] == 'D' && content[3] == 'F' && content[4] == '-';
    }

    @Override
    public ParsedDocument parse(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw invalid("暂不支持加密 PDF");
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount < 1 || pageCount > limits.getMaxPages()) {
                throw invalid("PDF 页数超过限制");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<ParsedBlock> blocks = new ArrayList<>();
            StringBuilder fullText = new StringBuilder();
            int paragraphIndex = 0;
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = normalize(stripper.getText(document));
                for (String part : pageText.split("\\n\\s*\\n|(?<=。)\\s*\\n")) {
                    String text = part.trim();
                    if (text.isEmpty()) {
                        continue;
                    }
                    int start = append(fullText, text);
                    blocks.add(new ParsedBlock(blocks.size(), page, paragraphIndex++, "EXACT_PAGE",
                            heading(text), text, start, start + text.length()));
                    enforceLimits(blocks.size(), fullText.length());
                }
            }
            if (fullText.toString().isBlank()) {
                throw invalid("PDF 未提取到文本，可能是扫描件，需要 OCR");
            }
            return new ParsedDocument("PDF", pageCount, "EXACT_PAGE", fullText.toString(), List.copyOf(blocks));
        } catch (ServiceException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalid("PDF 解析失败: " + safeMessage(ex));
        }
    }

    private String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n').replaceAll("[ \\t]+", " ").trim();
    }

    private int append(StringBuilder target, String text) {
        if (!target.isEmpty()) {
            target.append('\n');
        }
        int start = target.length();
        target.append(text);
        return start;
    }

    private String heading(String text) {
        String first = text.lines().findFirst().orElse(text);
        return first.length() <= 80 ? first : null;
    }

    private void enforceLimits(int blockCount, int characters) {
        if (blockCount > limits.getMaxBlocks() || characters > limits.getMaxCharacters()) {
            throw invalid("合同文本超过解析限制");
        }
    }

    private ServiceException invalid(String message) {
        return new ServiceException(message, ResultCode.INVALID_PARA.getCode());
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
