package com.anyujia.anyujialegalservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "legal.ai")
public class LegalAiProperties {
    private boolean enabled;
    private String apiKey = "";
    @NotBlank
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    @NotBlank
    private String chatModel = "qwen-plus";
    @Min(100)
    private int connectTimeoutMillis = 5000;
    @Min(100)
    private int readTimeoutMillis = 30000;
    @Min(1)
    private int failureThreshold = 2;
    @Min(1)
    private int cooldownSeconds = 60;
    @Valid
    private Embedding embedding = new Embedding();
    @Valid
    private Vector vector = new Vector();
    @Valid
    private Retrieval retrieval = new Retrieval();

    @Data
    public static class Embedding {
        private boolean enabled;
        @NotBlank
        private String model = "text-embedding-v4";
        @Min(64)
        @Max(4096)
        private int dimensions = 1024;
        @Min(1)
        @Max(64)
        private int batchSize = 16;
        private boolean cacheEnabled = true;
        @Min(1)
        private int cacheTtlSeconds = 86400;
        @Min(1)
        private long cacheMaxEntries = 2000;
    }

    @Data
    public static class Vector {
        private boolean enabled;
    }

    @Data
    public static class Retrieval {
        @Min(1)
        @Max(500)
        private int channelCandidateLimit = 50;
        @Min(1)
        @Max(500)
        private int rerankCandidateLimit = 30;
        @Min(1)
        @Max(1000)
        private int rrfK = 60;
        @DecimalMin("0.0")
        private double lexicalWeight = 1D;
        @DecimalMin("0.0")
        private double vectorWeight = 1.2D;
        @DecimalMin("0.0")
        private double intentWeight = 0.8D;
        @DecimalMin("0.0")
        private double rerankWeight = 0.35D;
    }
}
