package com.anyujia.anyujiachatservice.sharding;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.seata.rm.datasource.DataSourceProxy;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Physical sharding datasource wiring, active only when
 * {@code chat.message-sharding.physical-enabled=true}. Each physical MySQL instance becomes its own
 * Hikari pool wrapped in a Seata {@link DataSourceProxy} (AT mode), so a single
 * {@code @GlobalTransactional} service method can coordinate the main database and the shard
 * databases as independent branches.
 *
 * <p>The main datasource is wrapped <em>in place</em> by {@link SeataMainDataSourceProxyProcessor}
 * rather than a separate {@code @Primary} bean: registering another {@code DataSource} {@code @Bean}
 * would satisfy {@code DataSourceAutoConfiguration}'s
 * {@code @ConditionalOnMissingBean(DataSource.class)} and suppress the auto-configured
 * {@code dataSource}, which the MyBatis/JdbcTemplate wiring depends on.
 */
@Configuration
@ConditionalOnProperty(prefix = "chat.message-sharding", name = "physical-enabled", havingValue = "true")
public class ChatMessageShardDataSourceConfig {

    private final ChatMessageShardProperties properties;
    private final ChatMessageShardRouter shardRouter;

    public ChatMessageShardDataSourceConfig(ChatMessageShardProperties properties,
                                            ChatMessageShardRouter shardRouter) {
        this.properties = properties;
        this.shardRouter = shardRouter;
    }

    @Bean
    public ChatMessageShardStore chatMessageShardStore() {
        int instanceCount = shardRouter.mysqlInstanceCount();
        List<DataSource> shardDataSources = new ArrayList<>(instanceCount);
        for (int i = 0; i < instanceCount; i++) {
            ChatMessageShardProperties.ShardDataSource config = properties.getDatasources().get(i);
            if (config == null || config.getUrl() == null || config.getUrl().isBlank()) {
                throw new IllegalStateException("Missing datasource config for physical shard " + i);
            }
            HikariConfig hikari = new HikariConfig();
            hikari.setJdbcUrl(config.getUrl());
            hikari.setUsername(config.getUsername());
            hikari.setPassword(config.getPassword());
            hikari.setMaximumPoolSize(config.getMaximumPoolSize());
            hikari.setConnectionTimeout(config.getConnectionTimeout());
            hikari.setPoolName("chat-shard-" + i);
            shardDataSources.add(new DataSourceProxy(new HikariDataSource(hikari)));
        }
        return new ChatMessageShardStore(shardDataSources, shardRouter);
    }
}
