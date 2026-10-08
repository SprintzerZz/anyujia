package com.anyujia.anyujiacommoncore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anyujia.anyujiacommoncore.config.ThreadPoolConfig;
import com.anyujia.anyujiacommoncore.enums.RejectType;
import com.anyujia.anyujiacommoncore.monitor.CountingRejectedExecutionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolConfigTests {

    @Test
    void getThreadExecutorShouldApplyConfiguredProperties() {
        ThreadPoolConfig config = createConfig(2, 8, 16, 30, "test-thread-", RejectType.CALLER_RUNS_POLICY);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.getThreadExecutor();

        try {
            ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
            assertThat(threadPoolExecutor.getCorePoolSize()).isEqualTo(2);
            assertThat(threadPoolExecutor.getMaximumPoolSize()).isEqualTo(8);
            assertThat(threadPoolExecutor.getQueue().remainingCapacity()).isEqualTo(16);
            assertThat(threadPoolExecutor.getKeepAliveTime(TimeUnit.SECONDS)).isEqualTo(30);
            assertThat(threadPoolExecutor.getRejectedExecutionHandler())
                    .isInstanceOf(CountingRejectedExecutionHandler.class);
            CountingRejectedExecutionHandler handler =
                    (CountingRejectedExecutionHandler) threadPoolExecutor.getRejectedExecutionHandler();
            assertThat(handler.getDelegate()).isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("test-thread-");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void getRejectHandlerShouldReturnAbortPolicy() {
        ThreadPoolConfig config = createConfig(RejectType.ABORT_POLICY);

        RejectedExecutionHandler handler = config.getRejectHandler();

        assertThat(handler).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
    }

    @Test
    void getRejectHandlerShouldReturnCallerRunsPolicy() {
        ThreadPoolConfig config = createConfig(RejectType.CALLER_RUNS_POLICY);

        RejectedExecutionHandler handler = config.getRejectHandler();

        assertThat(handler).isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
    }

    @Test
    void getRejectHandlerShouldReturnDiscardOldestPolicy() {
        ThreadPoolConfig config = createConfig(RejectType.DISCARD_OLDEST_POLICY);

        RejectedExecutionHandler handler = config.getRejectHandler();

        assertThat(handler).isInstanceOf(ThreadPoolExecutor.DiscardOldestPolicy.class);
    }

    @Test
    void getRejectHandlerShouldReturnDiscardPolicyWhenConfiguredDiscardPolicy() {
        ThreadPoolConfig config = createConfig(RejectType.DISCARD_POLICY);

        RejectedExecutionHandler handler = config.getRejectHandler();

        assertThat(handler).isInstanceOf(ThreadPoolExecutor.DiscardPolicy.class);
    }

    @Test
    void getRejectHandlerShouldReturnDiscardPolicyWhenConfiguredUnknownValue() {
        ThreadPoolConfig config = createConfig(999);

        RejectedExecutionHandler handler = config.getRejectHandler();

        assertThat(handler).isInstanceOf(ThreadPoolExecutor.DiscardPolicy.class);
    }

    @Test
    void rejectTypeShouldExposeConfiguredValues() {
        assertThat(RejectType.ABORT_POLICY.getValue()).isEqualTo(1);
        assertThat(RejectType.CALLER_RUNS_POLICY.getValue()).isEqualTo(2);
        assertThat(RejectType.DISCARD_OLDEST_POLICY.getValue()).isEqualTo(3);
        assertThat(RejectType.DISCARD_POLICY.getValue()).isEqualTo(4);
    }

    @Test
    void threadExecutorShouldUseNacosConfiguredProperties() {
        new ApplicationContextRunner()
                .withUserConfiguration(ThreadPoolConfig.class)
                .withPropertyValues(
                        "thread.pool-executor.corePoolSize=20",
                        "thread.pool-executor.maxPoolSize=50",
                        "thread.pool-executor.queueCapacity=80",
                        "thread.pool-executor.keepAliveSeconds=60",
                        "thread.pool-executor.prefixName=thread-service-",
                        "thread.pool-executor.rejectHandler=2")
                .run(context -> {
                    ThreadPoolTaskExecutor executor = context.getBean("threadPoolTaskExecutor",
                            ThreadPoolTaskExecutor.class);
                    ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();

                    assertThat(threadPoolExecutor.getCorePoolSize()).isEqualTo(20);
                    assertThat(threadPoolExecutor.getMaximumPoolSize()).isEqualTo(50);
                    assertThat(threadPoolExecutor.getQueue().remainingCapacity()).isEqualTo(80);
                    assertThat(threadPoolExecutor.getKeepAliveTime(TimeUnit.SECONDS)).isEqualTo(60);
                    assertThat(executor.getThreadNamePrefix()).isEqualTo("thread-service-");
                    assertThat(threadPoolExecutor.getRejectedExecutionHandler())
                            .isInstanceOf(CountingRejectedExecutionHandler.class);
                    CountingRejectedExecutionHandler handler =
                            (CountingRejectedExecutionHandler) threadPoolExecutor.getRejectedExecutionHandler();
                    assertThat(handler.getDelegate()).isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
                });
    }

    @Test
    @Timeout(5)
    void threadExecutorShouldScaleToMaxPoolSizeWhenQueueIsFull() throws InterruptedException {
        // 创建线程池配置：核心线程数 2，最大线程数 4，队列容量 1，拒绝策略使用 AbortPolicy。
        ThreadPoolConfig config = createConfig(2, 4, 1, 60, "scale-thread-", RejectType.ABORT_POLICY);
        // 根据配置创建线程池执行器，后续通过它提交任务验证扩容行为。
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.getThreadExecutor();
        // 用于阻塞已经开始执行的任务，避免任务过快结束导致无法观察并发数。
        CountDownLatch releaseTasks = new CountDownLatch(1);
        // 用于标记线程池已经达到最大并发数 4。
        CountDownLatch maxConcurrencyReached = new CountDownLatch(1);
        // 记录当前正在执行的任务数量。
        AtomicInteger runningTasks = new AtomicInteger();
        // 记录测试过程中观察到的最大同时执行任务数量。
        AtomicInteger maxRunningTasks = new AtomicInteger();

        // 使用 try/finally 确保测试结束后释放阻塞任务并关闭线程池。
        try {
            // 提交 5 个任务：2 个进入核心线程，1 个进入队列，2 个触发线程池扩容到最大线程数 4。
            for (int i = 0; i < 5; i++) {
                // 向线程池提交一个会阻塞等待释放信号的任务。
                executor.execute(() -> {
                    // 任务开始执行时，将当前执行中的任务数加 1。
                    int running = runningTasks.incrementAndGet();
                    // 更新测试过程中观察到的最大并发任务数。
                    maxRunningTasks.updateAndGet(current -> Math.max(current, running));
                    // 当并发执行任务数达到 4 时，说明线程池已经扩容到最大线程数。
                    if (running == 4) {
                        // 释放等待断言的 latch，通知测试线程可以继续验证。
                        maxConcurrencyReached.countDown();
                    }
                    // 捕获任务阻塞等待期间可能出现的中断。
                    try {
                        // 阻塞当前任务，保持线程占用状态，便于观察线程池扩容后的并发数。
                        releaseTasks.await();
                    // 当前任务等待过程中如果被中断，会进入这里恢复中断标记。
                    } catch (InterruptedException ex) {
                        // 恢复线程中断状态，避免吞掉中断信号。
                        Thread.currentThread().interrupt();
                    // 无论任务正常释放还是被中断，都要减少当前运行任务计数。
                    } finally {
                        // 当前任务结束前，将运行中任务数减 1。
                        runningTasks.decrementAndGet();
                    }
                });
            }

            // 等待最多 1 秒，确认线程池实际达到过 4 个任务并发执行。
            assertThat(maxConcurrencyReached.await(1, TimeUnit.SECONDS)).isTrue();
            // 断言测试过程中记录到的最大并发任务数为 4。
            assertThat(maxRunningTasks).hasValue(4);
            // 断言线程池实际创建的线程数已经扩容到最大线程数 4。
            assertThat(executor.getThreadPoolExecutor().getPoolSize()).isEqualTo(4);
        // 测试结束后始终释放任务并关闭线程池，避免测试线程泄漏。
        } finally {
            // 释放所有阻塞中的任务，让它们可以正常结束。
            releaseTasks.countDown();
            // 关闭线程池，清理测试创建的线程资源。
            executor.shutdown();
        }
    }

    @Test
    @Timeout(5)
    void threadExecutorShouldRejectTaskWhenPoolAndQueueAreFull() throws InterruptedException {
        // 创建线程池配置：核心线程数 1，最大线程数 1，队列容量 1，拒绝策略使用 AbortPolicy。
        ThreadPoolConfig config = createConfig(1, 1, 1, 60, "reject-thread-", RejectType.ABORT_POLICY);
        // 根据配置创建线程池执行器，后续通过它提交任务验证满载拒绝行为。
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.getThreadExecutor();
        // 用于阻塞已提交任务，使线程和队列保持占满状态。
        CountDownLatch releaseTasks = new CountDownLatch(1);

        // 使用 try/finally 确保测试结束后释放阻塞任务并关闭线程池。
        try {
            // 提交第一个阻塞任务，占用唯一的工作线程。
            executor.execute(blockingTask(releaseTasks));
            // 提交第二个阻塞任务，占用唯一的队列容量。
            executor.execute(blockingTask(releaseTasks));

            // 在线程池线程数已达上限且队列已满时，再提交任务应触发拒绝策略。
            assertThatThrownBy(() -> executor.execute(() -> {
            // 这里的空任务用于触发提交动作，正常情况下不会被执行。
            })).isInstanceOf(RejectedExecutionException.class);
            assertThat(config.getMetrics().getRejectedTaskCount()).isEqualTo(1L);
        // 测试结束后始终释放任务并关闭线程池，避免测试线程泄漏。
        } finally {
            // 释放前两个阻塞任务，让它们可以结束。
            releaseTasks.countDown();
            // 关闭线程池，清理测试创建的线程资源。
            executor.shutdown();
        }
    }

    private Runnable blockingTask(CountDownLatch releaseTasks) {
        // 返回一个会等待释放信号的任务，用于稳定占用线程或队列。
        return () -> {
            // 捕获任务等待过程中可能出现的中断。
            try {
                // 阻塞当前任务，直到测试方法发出释放信号。
                releaseTasks.await();
            // 当前任务等待过程中如果被中断，会进入这里恢复中断标记。
            } catch (InterruptedException ex) {
                // 恢复线程中断状态，避免吞掉中断信号。
                Thread.currentThread().interrupt();
            }
        };
    }

    private ThreadPoolConfig createConfig(RejectType rejectType) {
        return createConfig(rejectType.getValue());
    }

    private ThreadPoolConfig createConfig(Integer rejectHandler) {
        return createConfig(5, 100, 100, 60, "thread-service-", rejectHandler);
    }

    private ThreadPoolConfig createConfig(
            Integer corePoolSize,
            Integer maxPoolSize,
            Integer queueCapacity,
            Integer keepAliveSeconds,
            String prefixName,
            RejectType rejectType) {
        return createConfig(corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds, prefixName, rejectType.getValue());
    }

    private ThreadPoolConfig createConfig(
            Integer corePoolSize,
            Integer maxPoolSize,
            Integer queueCapacity,
            Integer keepAliveSeconds,
            String prefixName,
            Integer rejectHandler) {
        ThreadPoolConfig config = new ThreadPoolConfig();
        ReflectionTestUtils.setField(config, "corePoolSize", corePoolSize);
        ReflectionTestUtils.setField(config, "maxPoolSize", maxPoolSize);
        ReflectionTestUtils.setField(config, "queueCapacity", queueCapacity);
        ReflectionTestUtils.setField(config, "keepAliveSeconds", keepAliveSeconds);
        ReflectionTestUtils.setField(config, "prefixName", prefixName);
        ReflectionTestUtils.setField(config, "rejectHandler", rejectHandler);
        return config;
    }
}
