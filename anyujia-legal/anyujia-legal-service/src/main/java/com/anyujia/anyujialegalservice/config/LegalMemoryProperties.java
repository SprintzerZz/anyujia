package com.anyujia.anyujialegalservice.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "legal.memory")
public class LegalMemoryProperties {
    private boolean redisEnabled = true;
    @Min(60)
    private long shortTermTtlSeconds = 1800;
    @Min(1)
    @Max(100)
    private int chatWindowLimit = 100;
}
