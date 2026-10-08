package com.anyujia.anyujiachatservice.service.moderation;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "chat.content-filter")
public class ChatContentFilterProperties {
    private boolean enabled = true;
    private Mode mode = Mode.REPLACE;
    private List<String> sensitiveWords = new ArrayList<>();
    private String replacement = "***";

    public enum Mode {
        REJECT,
        REPLACE
    }
}
