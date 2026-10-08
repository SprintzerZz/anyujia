package com.anyujia.anyujialegalservice.domain.model;

public record ParsedBlock(int blockIndex, Integer pageNumber, int paragraphIndex,
                          String locatorPrecision, String heading, String text,
                          int charStart, int charEnd) {
}
