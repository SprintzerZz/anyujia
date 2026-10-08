package com.anyujia.anyujiacommoncore.monitor;

import com.anyujia.anyujiacommoncore.config.ThreadPoolConfig;
import com.anyujia.anyujiacommoncore.domain.dto.ThreadPoolMetricsDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * 线程池 Actuator 监控端点。
 *
 * <p>访问 {@code /actuator/threadpool} 可以实时查看公共业务线程池当前运行状态。</p>
 */
@Endpoint(id = "threadpool")
@RequiredArgsConstructor
public class ThreadPoolMonitorEndpoint {

    /**
     * 公共线程池配置。
     */
    private final ThreadPoolConfig threadPoolConfig;

    /**
     * 读取线程池实时指标。
     *
     * @return 线程池实时指标
     */
    @ReadOperation
    public ThreadPoolMetricsDTO metrics() {
        return threadPoolConfig.getMetrics();
    }
}
