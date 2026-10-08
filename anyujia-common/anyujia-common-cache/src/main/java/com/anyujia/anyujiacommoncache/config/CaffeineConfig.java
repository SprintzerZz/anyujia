package com.anyujia.anyujiacommoncache.config;

import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine 本地缓存配置。
 *
 * <p>该配置创建一个进程内缓存对象，适合缓存短生命周期、允许本机内存命中的热点数据。
 * 本地缓存不会在多个服务实例之间同步，如果数据必须强一致，应优先使用 Redis 等集中式缓存。</p>
 */
@Configuration
public class CaffeineConfig {

    /**
     * 初始容量。
     *
     * <p>用于预估缓存容器的初始大小，合理设置可以减少运行时扩容开销。</p>
     */
    @Value("${caffeine.build.initial-capacity:100}")
    private Integer initialCapacity;

    /**
     * 最大容量。
     *
     * <p>缓存条目数量达到最大容量后，Caffeine 会按访问频率和最近访问情况自动淘汰旧数据。</p>
     */
    @Value("${caffeine.build.maximum-size:10000}")
    private Long maximumSize;

    /**
     * 写入后过期时间，单位秒。
     *
     * <p>缓存项从写入或更新开始计时，超过该时间后自动过期。</p>
     */
    @Value("${caffeine.build.expire:300}")
    private Long expire;

    /**
     * 构造本地缓存对象。
     *
     * <p>key 固定为 String，value 支持任意业务对象。过期策略使用 write-after-write，
     * 即写入后超过 {@code caffeine.build.expire} 秒自动失效。</p>
     *
     * @return 本地缓存对象
     */
    @Bean
    public Cache<String, Object> caffeineCache() {
        return Caffeine.newBuilder()
                .initialCapacity(initialCapacity)
                .maximumSize(maximumSize)
                .expireAfterWrite(expire, TimeUnit.SECONDS)
                .build();
    }
}
