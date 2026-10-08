package com.anyujia.anyujialegalservice.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "legal.document")
public class LegalDocumentProperties {
    @Min(1024)
    @Max(52428800)
    private long maxBytes = 26214400;
    @Min(1)
    @Max(1000)
    private int maxPages = 200;
    @Min(1000)
    @Max(2000000)
    private int maxCharacters = 500000;
    @Min(10)
    @Max(20000)
    private int maxBlocks = 5000;
}
