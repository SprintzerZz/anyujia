package com.anyujia.anyujiachatservice.service.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "chat.rate-limit.conversation")
public class ConversationRateLimitProperties {
    private boolean enabled = true;
    private int maxMessagesPerSecond = 50;
    private long idleExpireSeconds = 300;
}
