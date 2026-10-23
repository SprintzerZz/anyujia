package com.anyujia.anyujiachatservice.sharding;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Physical shard configuration. When {@code physicalEnabled} is false the application keeps the
 * single-database logical sharding behaviour; when true, message tables are routed to the
 * configured physical MySQL instances.
 */
@Data
@Component
@ConfigurationProperties(prefix = "chat.message-sharding")
public class ChatMessageShardProperties {
    /** Master switch for the physical multi-MySQL topology. */
    private boolean physicalEnabled = false;

    /** One entry per physical MySQL instance, indexed by {@code physicalMysqlIndex}. */
    private List<ShardDataSource> datasources = new ArrayList<>();

    @Data
    public static class ShardDataSource {
        private String url;
        private String username;
        private String password;
        private int maximumPoolSize = 10;
        private long connectionTimeout = 30_000;
    }
}
