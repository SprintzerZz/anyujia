package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.anyujia.anyujialegalservice.config.LegalDocumentProperties;
import com.anyujia.anyujialegalservice.domain.model.ParsedDocument;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocxLegalDocumentParserTest {
    @Test
    void usesSavedPageBreakAsPageHintAndKeepsParagraphIndex() throws Exception {
        byte[] content;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var first = document.createParagraph();
            first.createRun().setText("First clause");
            first.createRun().addBreak(BreakType.PAGE);
            document.createParagraph().createRun().setText("Second clause");
            document.write(output);
            content = output.toByteArray();
        }

        ParsedDocument parsed = new DocxLegalDocumentParser(new LegalDocumentProperties(),
                new TikaLegalDocumentExtractor()).parse(content);

        assertThat(parsed.documentType()).isEqualTo("DOCX");
        assertThat(parsed.blocks()).extracting(block -> block.pageNumber()).containsExactly(1, 2);
        assertThat(parsed.blocks()).extracting(block -> block.paragraphIndex()).containsExactly(0, 1);
        assertThat(parsed.locatorPrecision()).isEqualTo("DOCX_PAGE_HINT");
    }
}
