package com.anyujia.anyujialegalservice.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

/** Central Apache Tika extraction entry point for uploaded legal documents. */
@Component
public class TikaLegalDocumentExtractor {
    private final AutoDetectParser parser = new AutoDetectParser();

    public Extraction extract(byte[] content) {
        Metadata metadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler(-1);
        try {
            parser.parse(new ByteArrayInputStream(content), handler, metadata, new ParseContext());
            return new Extraction(normalize(handler.toString()), metadata);
        } catch (IOException | SAXException | TikaException | RuntimeException ex) {
            throw new IllegalArgumentException("Apache Tika 文档解析失败: " + safeMessage(ex), ex);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n")
                .replace('\r', '\n').replaceAll("[ \\t]+", " ").trim();
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    public record Extraction(String text, Metadata metadata) {
        public int pageCount() {
            String pages = metadata.get("xmpTPg:NPages");
            if (pages == null) {
                pages = metadata.get("pageCount");
            }
            try {
                return pages == null ? 0 : Integer.parseInt(pages);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        public boolean containsPageSeparator() {
            return text.indexOf('\f') >= 0;
        }
    }
}
