package com.anyujia.anyujiacommoncore.config;

import com.anyujia.anyujiacommoncore.enums.RejectType;
import com.anyujia.anyujiacommoncore.domain.dto.ThreadPoolMetricsDTO;
import com.anyujia.anyujiacommoncore.monitor.CountingRejectedExecutionHandler;
import com.anyujia.anyujiacommoncore.monitor.ThreadPoolMonitorEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程池配置。
 *
 * <p>开启 Spring 异步任务能力，并提供统一的 {@link ThreadPoolTaskExecutor}
 * 供 {@code @Async} 或业务代码注入使用。线程池参数可通过配置文件中的
 * {@code thread.pool-executor.*} 属性覆盖。</p>
 */
@Slf4j
@EnableAsync
@Configuration
public class ThreadPoolConfig {

    private static final String CORE_POOL_SIZE_KEY = "thread.pool-executor.corePoolSize";

    private static final String MAX_POOL_SIZE_KEY = "thread.pool-executor.maxPoolSize";

    private static final String QUEUE_CAPACITY_KEY = "thread.pool-executor.queueCapacity";

    private static final String KEEP_ALIVE_SECONDS_KEY = "thread.pool-executor.keepAliveSeconds";

    private static final String PREFIX_NAME_KEY = "thread.pool-executor.prefixName";

    private static final String REJECT_HANDLER_KEY = "thread.pool-executor.rejectHandler";

    private final Environment environment;

    /**
     * 核心线程数。
     *
     * <p>线程池会尽量保留核心线程处理任务，即使线程处于空闲状态。</p>
     */
    @Value("${thread.pool-executor.corePoolSize:5}")
    private Integer corePoolSize;

    /**
     * 最大线程数。
     *
     * <p>当核心线程都在忙且阻塞队列已满时，线程池最多可扩容到该数量。</p>
     */
    @Value("${thread.pool-executor.maxPoolSize:100}")
    private Integer maxPoolSize;

    /**
     * 阻塞队列大小。
     *
     * <p>当核心线程都在执行任务时，新任务会先进入队列等待执行。</p>
     */
    @Value("${thread.pool-executor.queueCapacity:100}")
    private Integer queueCapacity;

    /**
     * 非核心线程空闲存活时间，单位：秒。
     */
    @Value("${thread.pool-executor.keepAliveSeconds:60}")
    private Integer keepAliveSeconds;

    /**
     * 线程名称前缀。
     *
     * <p>便于在日志、链路追踪或线程 dump 中定位异步任务来源。</p>
     */
    @Value("${thread.pool-executor.prefixName:thread-service-}")
    private String prefixName;

    /**
     * 拒绝策略。
     *
     * <p>可选值参考 {@link RejectType}，默认使用 {@link ThreadPoolExecutor.CallerRunsPolicy}。</p>
     */
    @Value("${thread.pool-executor.rejectHandler:2}")
    private Integer rejectHandler;

    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /**
     * 线程池拒绝任务计数器。
     */
    private final AtomicLong rejectedTaskCount = new AtomicLong();

    public ThreadPoolConfig() {
        this.environment = null;
    }

    @Autowired
    public ThreadPoolConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * 注册线程池执行器。
     *
     * @return 线程池执行器
     */
    @Bean("threadPoolTaskExecutor")
    public Executor getThreadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix(prefixName);
        executor.setRejectedExecutionHandler(getCountingRejectHandler());
        executor.initialize();
        this.threadPoolTaskExecutor = executor;
        return executor;
    }

    /**
     * 注册线程池 Actuator 监控端点。
     *
     * @return 线程池监控端点
     */
    @Bean
    @ConditionalOnClass(Endpoint.class)
    public ThreadPoolMonitorEndpoint threadPoolMonitorEndpoint() {
        return new ThreadPoolMonitorEndpoint(this);
    }

    /**
     * 监听 Nacos 配置刷新事件，动态更新线程池可变参数。
     *
     * <p>{@code queueCapacity} 由底层阻塞队列在初始化时确定，运行中不做修改；
     * 如果需要调整队列容量，需要重启服务或改造成可变容量队列。</p>
     *
     * @param event Spring Cloud 配置变更事件
     */
    @EventListener(EnvironmentChangeEvent.class)
    public void refreshThreadPool(EnvironmentChangeEvent event) {
        if (environment == null || threadPoolTaskExecutor == null || event == null || event.getKeys() == null) {
            return;
        }
        boolean changed = event.getKeys().stream().anyMatch(key -> key.startsWith("thread.pool-executor."));
        if (!changed) {
            return;
        }

        Integer newCorePoolSize = environment.getProperty(CORE_POOL_SIZE_KEY, Integer.class, corePoolSize);
        Integer newMaxPoolSize = environment.getProperty(MAX_POOL_SIZE_KEY, Integer.class, maxPoolSize);
        Integer newKeepAliveSeconds = environment.getProperty(KEEP_ALIVE_SECONDS_KEY, Integer.class, keepAliveSeconds);
        String newPrefixName = environment.getProperty(PREFIX_NAME_KEY, prefixName);
        Integer newRejectHandler = environment.getProperty(REJECT_HANDLER_KEY, Integer.class, rejectHandler);

        if (newCorePoolSize <= 0 || newMaxPoolSize <= 0 || newCorePoolSize > newMaxPoolSize) {
            log.warn("动态线程池配置非法，忽略本次刷新：corePoolSize={}, maxPoolSize={}",
                    newCorePoolSize, newMaxPoolSize);
            return;
        }

        updatePoolSize(newCorePoolSize, newMaxPoolSize);
        threadPoolTaskExecutor.setKeepAliveSeconds(newKeepAliveSeconds);
        threadPoolTaskExecutor.setThreadNamePrefix(newPrefixName);

        this.rejectHandler = newRejectHandler;
        threadPoolTaskExecutor.setRejectedExecutionHandler(getCountingRejectHandler());

        this.corePoolSize = newCorePoolSize;
        this.maxPoolSize = newMaxPoolSize;
        this.keepAliveSeconds = newKeepAliveSeconds;
        this.prefixName = newPrefixName;

        if (event.getKeys().contains(QUEUE_CAPACITY_KEY)) {
            log.warn("queueCapacity 已变更但不会动态生效，当前线程池队列容量需要重启服务后重新创建");
        }
        log.info("动态线程池刷新完成：corePoolSize={}, maxPoolSize={}, keepAliveSeconds={}, prefixName={}, rejectHandler={}",
                corePoolSize, maxPoolSize, keepAliveSeconds, prefixName, rejectHandler);
    }

    private void updatePoolSize(Integer newCorePoolSize, Integer newMaxPoolSize) {
        ThreadPoolExecutor executor = threadPoolTaskExecutor.getThreadPoolExecutor();
        if (newCorePoolSize > executor.getMaximumPoolSize()) {
            threadPoolTaskExecutor.setMaxPoolSize(newMaxPoolSize);
            threadPoolTaskExecutor.setCorePoolSize(newCorePoolSize);
            return;
        }
        threadPoolTaskExecutor.setCorePoolSize(newCorePoolSize);
        threadPoolTaskExecutor.setMaxPoolSize(newMaxPoolSize);
    }

    /**
     * 根据配置值获取线程池拒绝策略。
     *
     * @return 拒绝策略处理器
     */
    public RejectedExecutionHandler getRejectHandler() {
        if (RejectType.ABORT_POLICY.getValue().equals(rejectHandler)) {
            return new ThreadPoolExecutor.AbortPolicy();
        } else if (RejectType.CALLER_RUNS_POLICY.getValue().equals(rejectHandler)) {
            return new ThreadPoolExecutor.CallerRunsPolicy();
        } else if (RejectType.DISCARD_OLDEST_POLICY.getValue().equals(rejectHandler)) {
            return new ThreadPoolExecutor.DiscardOldestPolicy();
        }
        return new ThreadPoolExecutor.DiscardPolicy();
    }

    /**
     * 获取带拒绝计数能力的拒绝策略。
     *
     * @return 拒绝策略处理器
     */
    private RejectedExecutionHandler getCountingRejectHandler() {
        return new CountingRejectedExecutionHandler(getRejectHandler(), rejectedTaskCount);
    }

    /**
     * 获取线程池实时运行指标。
     *
     * @return 线程池实时运行指标
     */
    public ThreadPoolMetricsDTO getMetrics() {
        ThreadPoolMetricsDTO metrics = new ThreadPoolMetricsDTO();
        metrics.setCorePoolSize(corePoolSize);
        metrics.setMaximumPoolSize(maxPoolSize);
        metrics.setThreadNamePrefix(prefixName);
        metrics.setRejectedTaskCount(rejectedTaskCount.get());

        if (threadPoolTaskExecutor == null || threadPoolTaskExecutor.getThreadPoolExecutor() == null) {
            metrics.setPoolSize(0);
            metrics.setActiveCount(0);
            metrics.setLargestPoolSize(0);
            metrics.setQueueSize(0);
            metrics.setQueueCapacity(queueCapacity);
            metrics.setQueueRemainingCapacity(queueCapacity);
            metrics.setTaskCount(0L);
            metrics.setCompletedTaskCount(0L);
            return metrics;
        }

        ThreadPoolExecutor executor = threadPoolTaskExecutor.getThreadPoolExecutor();
        int queueSize = executor.getQueue().size();
        int queueRemainingCapacity = executor.getQueue().remainingCapacity();
        metrics.setCorePoolSize(executor.getCorePoolSize());
        metrics.setMaximumPoolSize(executor.getMaximumPoolSize());
        metrics.setPoolSize(executor.getPoolSize());
        metrics.setActiveCount(executor.getActiveCount());
        metrics.setLargestPoolSize(executor.getLargestPoolSize());
        metrics.setQueueSize(queueSize);
        metrics.setQueueCapacity(queueSize + queueRemainingCapacity);
        metrics.setQueueRemainingCapacity(queueRemainingCapacity);
        metrics.setTaskCount(executor.getTaskCount());
        metrics.setCompletedTaskCount(executor.getCompletedTaskCount());
        return metrics;
    }
}
