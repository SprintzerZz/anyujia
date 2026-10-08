package com.anyujia.anyujiacommoncore.monitor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 带拒绝计数能力的线程池拒绝策略包装器。
 */
public class CountingRejectedExecutionHandler implements RejectedExecutionHandler {

    /**
     * 原始拒绝策略。
     */
    private final RejectedExecutionHandler delegate;

    /**
     * 拒绝任务计数器。
     */
    private final AtomicLong rejectedTaskCount;

    public CountingRejectedExecutionHandler(RejectedExecutionHandler delegate, AtomicLong rejectedTaskCount) {
        this.delegate = delegate;
        this.rejectedTaskCount = rejectedTaskCount;
    }

    /**
     * 记录拒绝次数并委托给原始拒绝策略处理。
     *
     * @param runnable 被拒绝的任务
     * @param executor 当前线程池
     */
    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        rejectedTaskCount.incrementAndGet();
        delegate.rejectedExecution(runnable, executor);
    }

    /**
     * 获取原始拒绝策略。
     *
     * @return 原始拒绝策略
     */
    public RejectedExecutionHandler getDelegate() {
        return delegate;
    }
}
