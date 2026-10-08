package com.anyujia.anyujialegalservice.domain.model;

import java.util.List;

public record ParsedDocument(String documentType, int pageCount, String locatorPrecision,
                             String fullText, List<ParsedBlock> blocks) {
}
