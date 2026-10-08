package com.anyujia.anyujialegalservice.service;

import com.anyujia.anyujialegalservice.domain.model.ParsedDocument;

public interface LegalDocumentParser {
    boolean supports(String filename, byte[] content);

    ParsedDocument parse(byte[] content);
}
