package com.anyujia.anyujiaadminservice.map.config;

import java.util.concurrent.TimeUnit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 地图缓存配置，从 Nacos 的 map.cache 前缀读取。
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "map.cache")
public class MapCacheProperties {

    /**
     * 城市列表缓存 key。
     */
    private String cityListKey = "cache:map:city:list:v2";

    /**
     * 城市拼音归类缓存 key。
     */
    private String cityPinyinKey = "cache:map:city:pinyin:v1";

    /**
     * 热门城市缓存 key。
     */
    private String cityHotKey = "cache:map:city:hot:v1";

    /**
     * 城市缓存过期时间。
     */
    private Long cityTimeout = 120L;

    /**
     * 城市缓存过期时间单位。
     */
    private TimeUnit cityTimeoutUnit = TimeUnit.MINUTES;

    /**
     * 城市缓存逻辑过期时间。
     */
    private Long cityLogicalTimeout = 30L;

    /**
     * 城市缓存逻辑过期时间单位。
     */
    private TimeUnit cityLogicalTimeoutUnit = TimeUnit.MINUTES;

    /**
     * Redis TTL 随机抖动最大秒数。
     */
    private Long ttlJitterSeconds = 300L;

    /**
     * 城市缓存版本号 key。
     */
    private String cityVersionKey = "cache:map:city:version";

    /**
     * 城市缓存重建分布式锁 key。
     */
    private String cityReloadLockKey = "lock:map:city:reload";

    /**
     * 城市缓存逻辑过期重建锁等待时间，单位毫秒。
     */
    private Long cityReloadLockWaitMillis = 0L;

    /**
     * 城市缓存重建锁持有时间，单位毫秒。
     */
    private Long cityReloadLockExpireMillis = 30000L;

    /**
     * 是否开启多实例本地缓存失效广播。
     */
    private Boolean invalidateBroadcastEnabled = false;

    /**
     * 城市缓存失效广播交换机。
     */
    private String invalidateExchange = "anyujia.map.cache.invalidate.fanout";

    /**
     * 城市缓存失效死信交换机。
     */
    private String invalidateDeadLetterExchange = "anyujia.map.cache.invalidate.dlx";

    /**
     * 城市缓存失效死信队列。
     */
    private String invalidateDeadLetterQueue = "anyujia.map.cache.invalidate.dlq";

    /**
     * 行政区划 BloomFilter key。
     */
    private String regionBloomKey = "bf:map:region:code";

    /**
     * BloomFilter 预计元素数量。
     */
    private Long regionBloomExpectedInsertions = 100000L;

    /**
     * BloomFilter 误判率。
     */
    private Double regionBloomFalseProbability = 0.001D;

    /**
     * 是否开启缓存一致性校验 Job。
     */
    private Boolean consistencyCheckEnabled = false;

    /**
     * 缓存一致性校验 Job cron 表达式。
     */
    private String consistencyCheckCron = "0 0/30 * * * ?";
}
