package com.anyujia.anyujiacommoncore.enums;

/**
 * 线程池拒绝策略类型。
 */
public enum RejectType {

    /**
     * 直接抛出 {@link java.util.concurrent.RejectedExecutionException}。
     */
    ABORT_POLICY(1),

    /**
     * 由提交任务的调用线程执行该任务，用于降低任务提交速度。
     */
    CALLER_RUNS_POLICY(2),

    /**
     * 丢弃队列中最旧的任务，然后尝试重新提交当前任务。
     */
    DISCARD_OLDEST_POLICY(3),

    /**
     * 直接丢弃当前任务，不抛出异常。
     */
    DISCARD_POLICY(4);

    /**
     * 配置文件中使用的策略编码。
     */
    private final Integer value;

    RejectType(Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}
