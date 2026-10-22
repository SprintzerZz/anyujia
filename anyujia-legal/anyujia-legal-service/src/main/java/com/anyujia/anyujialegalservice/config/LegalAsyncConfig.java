package com.anyujia.anyujialegalservice.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Configuration
public class LegalAsyncConfig {
    @Autowired
    private MeterRegistry meterRegistry;
    @Value("${legal.async.core-pool-size:2}")
    private int corePoolSize;
    @Value("${legal.async.max-pool-size:4}")
    private int maxPoolSize;
    @Value("${legal.async.queue-capacity:100}")
    private int queueCapacity;
    @Value("${legal.async.await-termination-seconds:30}")
    private int awaitTerminationSeconds;
    @Value("${legal.agent.core-pool-size:4}")
    private int agentCorePoolSize;
    @Value("${legal.agent.max-pool-size:16}")
    private int agentMaxPoolSize;
    @Value("${legal.agent.queue-capacity:200}")
    private int agentQueueCapacity;
    @Value("${legal.agent.submit.max-in-flight:10}")
    private int submitMaxInFlight;
    @Value("${legal.sse.scheduler-pool-size:4}")
    private int sseSchedulerPoolSize;
    @Value("${legal.sse.max-connections:2000}")
    private int sseMaxConnections;
    @Value("${legal.sse.writer-core-pool-size:8}")
    private int sseWriterCorePoolSize;
    @Value("${legal.sse.writer-max-pool-size:32}")
    private int sseWriterMaxPoolSize;
    @Value("${legal.sse.writer-queue-capacity:2000}")
    private int sseWriterQueueCapacity;
    @Bean(name = "legalAssessmentExecutor")
    public Executor legalAssessmentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("legal-assessment-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        registerExecutorMetrics("assessment", executor);
        log.info("Legal assessment executor configured: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }

    @Bean(name = "legalAgentExecutor")
    public Executor legalAgentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(agentCorePoolSize);
        executor.setMaxPoolSize(agentMaxPoolSize);
        executor.setQueueCapacity(agentQueueCapacity);
        executor.setThreadNamePrefix("legal-agent-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        registerExecutorMetrics("agent", executor);
        log.info("Legal Agent executor configured: core={}, max={}, queue={}",
                agentCorePoolSize, agentMaxPoolSize, agentQueueCapacity);
        return executor;
    }

    /**
     * A shared scheduler keeps SSE connections from consuming one long-lived
     * business thread each. The task only performs a short status read and
     * write, then yields back to the scheduler.
     */
    @Bean(name = "legalSseScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService legalSseScheduler() {
        return Executors.newScheduledThreadPool(Math.max(1, sseSchedulerPoolSize), runnable -> {
            Thread thread = new Thread(runnable, "legal-sse-scheduler");
            thread.setDaemon(false);
            return thread;
        });
    }

    @Bean(name = "legalSseConnectionLimiter")
    public Semaphore legalSseConnectionLimiter() {
        return new Semaphore(Math.max(1, sseMaxConnections));
    }

    @Bean(name = "legalAgentSubmitLimiter")
    public Semaphore legalAgentSubmitLimiter() {
        return new Semaphore(Math.max(1, submitMaxInFlight));
    }

    @Bean(name = "legalSseWriterExecutor")
    public Executor legalSseWriterExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(sseWriterCorePoolSize);
        executor.setMaxPoolSize(Math.max(sseWriterCorePoolSize, sseWriterMaxPoolSize));
        executor.setQueueCapacity(sseWriterQueueCapacity);
        executor.setThreadNamePrefix("legal-sse-writer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        registerExecutorMetrics("sse-writer", executor);
        log.info("Legal SSE writer executor configured: core={}, max={}, queue={}",
                sseWriterCorePoolSize, sseWriterMaxPoolSize, sseWriterQueueCapacity);
        return executor;
    }

    private void registerExecutorMetrics(String name, ThreadPoolTaskExecutor executor) {
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        Gauge.builder("legal.executor.active", pool, ThreadPoolExecutor::getActiveCount)
                .tag("executor", name).strongReference(true).register(meterRegistry);
        Gauge.builder("legal.executor.pool.size", pool, ThreadPoolExecutor::getPoolSize)
                .tag("executor", name).strongReference(true).register(meterRegistry);
        Gauge.builder("legal.executor.queue.size", pool, value -> value.getQueue().size())
                .tag("executor", name).strongReference(true).register(meterRegistry);
        Gauge.builder("legal.executor.queue.remaining", pool, value -> value.getQueue().remainingCapacity())
                .tag("executor", name).strongReference(true).register(meterRegistry);
    }
}
