package com.anyujia.anyujialegalservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.anyujia.anyujialegalservice.config.LegalDocumentProperties;
import com.anyujia.anyujialegalservice.domain.model.ParsedDocument;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfLegalDocumentParserTest {
    @Test
    void preservesExactPageForExtractedBlocks() throws Exception {
        byte[] content;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addPage(document, "Deposit is refundable.");
            addPage(document, "Landlord may enter directly.");
            document.save(output);
            content = output.toByteArray();
        }

        ParsedDocument parsed = new PdfLegalDocumentParser(limits()).parse(content);

        assertThat(parsed.documentType()).isEqualTo("PDF");
        assertThat(parsed.pageCount()).isEqualTo(2);
        assertThat(parsed.blocks()).extracting(block -> block.pageNumber()).containsExactly(1, 2);
        assertThat(parsed.blocks()).allMatch(block -> "EXACT_PAGE".equals(block.locatorPrecision()));
    }

    private void addPage(PDDocument document, String text) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.newLineAtOffset(72, 700);
            stream.showText(text);
            stream.endText();
        }
    }

    private LegalDocumentProperties limits() {
        return new LegalDocumentProperties();
    }
}
