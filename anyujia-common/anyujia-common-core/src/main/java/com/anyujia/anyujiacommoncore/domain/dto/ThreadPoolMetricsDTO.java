package com.anyujia.anyujiacommoncore.domain.dto;

import lombok.Data;

/**
 * 线程池实时运行指标 DTO。
 */
@Data
public class ThreadPoolMetricsDTO {

    /**
     * 核心线程数。
     */
    private Integer corePoolSize;

    /**
     * 最大线程数。
     */
    private Integer maximumPoolSize;

    /**
     * 当前线程数。
     */
    private Integer poolSize;

    /**
     * 当前活跃线程数。
     */
    private Integer activeCount;

    /**
     * 历史最大线程数。
     */
    private Integer largestPoolSize;

    /**
     * 当前队列任务数。
     */
    private Integer queueSize;

    /**
     * 队列总容量。
     */
    private Integer queueCapacity;

    /**
     * 队列剩余容量。
     */
    private Integer queueRemainingCapacity;

    /**
     * 已提交任务总数。
     */
    private Long taskCount;

    /**
     * 已完成任务总数。
     */
    private Long completedTaskCount;

    /**
     * 已拒绝任务总数。
     */
    private Long rejectedTaskCount;

    /**
     * 线程名前缀。
     */
    private String threadNamePrefix;
}
