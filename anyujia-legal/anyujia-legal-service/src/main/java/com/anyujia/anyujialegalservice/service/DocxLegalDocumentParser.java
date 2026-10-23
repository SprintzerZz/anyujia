package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.config.LegalDocumentProperties;
import com.anyujia.anyujialegalservice.domain.model.ParsedBlock;
import com.anyujia.anyujialegalservice.domain.model.ParsedDocument;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocxLegalDocumentParser implements LegalDocumentParser {
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
    private final LegalDocumentProperties limits;
    private final TikaLegalDocumentExtractor tikaExtractor;

    @Override
    public boolean supports(String filename, byte[] content) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".docx") && content.length >= ZIP_MAGIC.length
                && content[0] == ZIP_MAGIC[0] && content[1] == ZIP_MAGIC[1]
                && content[2] == ZIP_MAGIC[2] && content[3] == ZIP_MAGIC[3];
    }

    @Override
    public ParsedDocument parse(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            // Tika is the canonical content extraction path. POI is used only to recover
            // DOCX-specific evidence locators that Tika does not expose reliably.
            TikaLegalDocumentExtractor.Extraction extraction = tikaExtractor.extract(content);
            String fullText = extraction.text();
            if (fullText.isBlank()) {
                throw invalid("DOCX 未提取到可分析文本");
            }
            List<DocxLocator> locators = new ArrayList<>();
            int pageHint = 1;
            int paragraphIndex = 0;
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = normalize(paragraph.getText());
                    if (!text.isBlank()) {
                        locators.add(new DocxLocator(pageHint, paragraphIndex,
                                isHeading(paragraph) ? text : null, text));
                    }
                    paragraphIndex++;
                    if (containsSavedPageBreak(paragraph)) {
                        pageHint++;
                    }
                } else if (element instanceof XWPFTable table) {
                    String text = normalize(table.getText());
                    if (!text.isBlank()) {
                        locators.add(new DocxLocator(pageHint, paragraphIndex++, null, text));
                    }
                }
            }
            List<ParsedBlock> blocks = toBlocks(fullText, locators);
            if (blocks.isEmpty()) {
                blocks = List.of(new ParsedBlock(0, 1, 0, "DOCX_PAGE_HINT", null,
                        fullText, 0, fullText.length()));
            }
            enforceLimits(blocks.size(), fullText.length());
            return new ParsedDocument("DOCX", pageHint, "DOCX_PAGE_HINT",
                    fullText, List.copyOf(blocks));
        } catch (ServiceException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw invalid("DOCX 解析失败: " + safeMessage(ex));
        }
    }

    private boolean containsSavedPageBreak(XWPFParagraph paragraph) {
        String xml = paragraph.getCTP().xmlText();
        return xml.contains("w:type=\"page\"") || xml.contains("w:lastRenderedPageBreak")
                || paragraph.getText().indexOf('\f') >= 0;
    }

    private boolean isHeading(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        return style != null && style.toLowerCase(Locale.ROOT).startsWith("heading");
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ')
                .replaceAll("\\s+", " ").trim();
    }

    private List<ParsedBlock> toBlocks(String fullText, List<DocxLocator> locators) {
        List<ParsedBlock> blocks = new ArrayList<>();
        int searchFrom = 0;
        for (DocxLocator locator : locators) {
            int start = fullText.indexOf(locator.text(), searchFrom);
            if (start < 0) {
                // Tika may normalize a table or field differently from POI. Keep the locator
                // usable while preserving Tika's full text as the canonical source.
                start = Math.min(searchFrom, fullText.length());
            }
            int end = Math.min(fullText.length(), start + locator.text().length());
            String blockText = start < fullText.length() ? fullText.substring(start, end) : locator.text();
            blocks.add(new ParsedBlock(blocks.size(), locator.pageNumber(), locator.paragraphIndex(),
                    "DOCX_PAGE_HINT", locator.heading(), blockText, start, end));
            searchFrom = Math.max(searchFrom, end);
        }
        return blocks;
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

    private record DocxLocator(int pageNumber, int paragraphIndex, String heading, String text) {
    }
}
