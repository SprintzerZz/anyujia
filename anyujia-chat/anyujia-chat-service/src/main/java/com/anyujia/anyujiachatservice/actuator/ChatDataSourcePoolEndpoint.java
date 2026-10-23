package com.anyujia.anyujiachatservice.actuator;

import com.anyujia.anyujiachatservice.metrics.ChatDataSourcePoolMetrics;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/** Read-only snapshot of every chat datasource pool (main + physical shards). */
@Component
@Endpoint(id = "chatDataSourcePools")
@RequiredArgsConstructor
public class ChatDataSourcePoolEndpoint {
    private final ChatDataSourcePoolMetrics poolMetrics;

    @ReadOperation
    public Map<String, Map<String, Object>> pools() {
        return poolMetrics.snapshot();
    }
}
