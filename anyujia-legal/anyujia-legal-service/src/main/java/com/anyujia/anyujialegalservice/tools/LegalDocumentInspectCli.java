package com.anyujia.anyujialegalservice.tools;

import com.anyujia.anyujialegalservice.config.LegalDocumentProperties;
import com.anyujia.anyujialegalservice.domain.model.ParsedBlock;
import com.anyujia.anyujialegalservice.domain.model.ParsedDocument;
import com.anyujia.anyujialegalservice.service.PdfLegalDocumentParser;
import com.anyujia.anyujialegalservice.service.TikaLegalDocumentExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Inspects PDF parsing and exact-page evidence locations without starting Spring. */
public final class LegalDocumentInspectCli {
    private LegalDocumentInspectCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: LegalDocumentInspectCli <pdf-path>");
        }
        Path path = Path.of(args[0]).toAbsolutePath().normalize();
        ParsedDocument parsed = new PdfLegalDocumentParser(new LegalDocumentProperties(),
                new TikaLegalDocumentExtractor())
                .parse(Files.readAllBytes(path));
        System.out.printf("type=%s pages=%d blocks=%d characters=%d precision=%s%n",
                parsed.documentType(), parsed.pageCount(), parsed.blocks().size(),
                parsed.fullText().length(), parsed.locatorPrecision());
        for (String keyword : new String[]{"押金", "维修", "解除", "不动产权"}) {
            Set<Integer> pages = new LinkedHashSet<>();
            for (ParsedBlock block : parsed.blocks()) {
                if (block.text().contains(keyword)) {
                    pages.add(block.pageNumber());
                }
            }
            System.out.printf("keyword=%s pages=%s%n", keyword, pages);
        }
    }
}
