package com.anyujia.anyujiachatservice.metrics;

import com.anyujia.anyujiachatservice.sharding.ChatMessageShardStore;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.seata.rm.datasource.DataSourceProxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Observes every chat datasource pool (main + physical shards) so saturation can be spotted
 * before it shows up as a message write failure. Exposes HikariCP counters as Micrometer gauges
 * and as a plain snapshot for the {@code chatDataSourcePools} actuator endpoint.
 */
@Component
public class ChatDataSourcePoolMetrics {

    private final List<PoolRef> pools;

    public ChatDataSourcePoolMetrics(MeterRegistry registry,
                                     @Qualifier("dataSource") DataSource mainDataSource,
                                     ObjectProvider<ChatMessageShardStore> shardStoreProvider) {
        this.pools = new ArrayList<>();
        pools.add(new PoolRef("main", unwrap(mainDataSource)));

        ChatMessageShardStore store = shardStoreProvider.getIfAvailable();
        if (store != null) {
            for (int i = 0; i < store.templates().size(); i++) {
                pools.add(new PoolRef("shard-" + i,
                        unwrap(store.templates().get(i).getDataSource())));
            }
        }

        for (PoolRef pool : pools) {
            Tags tags = Tags.of("pool", pool.name());
            HikariDataSource hikari = pool.hikari();
            registry.gauge("hikaricp_connections_active", tags, hikari, h -> active(h));
            registry.gauge("hikaricp_connections_idle", tags, hikari, h -> idle(h));
            registry.gauge("hikaricp_connections_pending", tags, hikari, h -> pending(h));
            registry.gauge("hikaricp_connections_total", tags, hikari, h -> total(h));
            registry.gauge("hikaricp_connections_max", tags, hikari, HikariDataSource::getMaximumPoolSize);
            registry.gauge("hikaricp_connection_timeout_millis", tags, hikari,
                    HikariDataSource::getConnectionTimeout);
        }
    }

    public Map<String, Map<String, Object>> snapshot() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (PoolRef pool : pools) {
            HikariDataSource hikari = pool.hikari();
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("active", active(hikari));
            stats.put("idle", idle(hikari));
            stats.put("pending", pending(hikari));
            stats.put("total", total(hikari));
            stats.put("maximumPoolSize", hikari.getMaximumPoolSize());
            stats.put("connectionTimeoutMillis", hikari.getConnectionTimeout());
            result.put(pool.name(), stats);
        }
        return result;
    }

    private static HikariDataSource unwrap(DataSource dataSource) {
        DataSource current = dataSource;
        while (current instanceof DataSourceProxy proxy) {
            current = proxy.getTargetDataSource();
        }
        if (current instanceof HikariDataSource hikari) {
            return hikari;
        }
        throw new IllegalStateException("Chat datasource is not a HikariCP pool: " + current.getClass());
    }

    private static int active(HikariDataSource hikari) {
        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        return pool == null ? 0 : pool.getActiveConnections();
    }

    private static int idle(HikariDataSource hikari) {
        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        return pool == null ? 0 : pool.getIdleConnections();
    }

    private static int pending(HikariDataSource hikari) {
        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        return pool == null ? 0 : pool.getThreadsAwaitingConnection();
    }

    private static int total(HikariDataSource hikari) {
        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        return pool == null ? 0 : pool.getTotalConnections();
    }

    private record PoolRef(String name, HikariDataSource hikari) {
    }
}
