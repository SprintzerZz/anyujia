package com.anyujia.anyujiacommoncache.utils;

import java.util.concurrent.TimeUnit;

import com.anyujia.anyujiacommonredis.service.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;

/**
 * 两级缓存工具类。
 *
 * <p>两级缓存由本地 Caffeine 缓存和 Redis 缓存组成。读取时优先读取本地缓存，
 * 本地未命中再读取 Redis；Redis 命中后会回填本地缓存。写入时同时更新 Redis 和本地缓存。</p>
 *
 * <p>该工具类不持有状态，调用方需要显式传入 {@link RedisService} 和 Caffeine
 * {@link Cache} 实例，方便在不同业务模块中复用。</p>
 */
@Slf4j
public final class CacheUtil {

    private CacheUtil() {
    }

    /**
     * 读取两级缓存数据。
     *
     * <p>读取顺序为：先查本地缓存，命中则直接返回；本地缓存未命中时查询 Redis，
     * Redis 命中后写回本地缓存，减少后续 Redis 访问。</p>
     *
     * @param redisService Redis 服务
     * @param key 缓存 key
     * @param typeReference 缓存对象泛型类型
     * @param caffeineCache 本地缓存服务
     * @param <T> 缓存对象类型
     * @return 缓存对象；两级缓存都未命中时返回 null
     */
    @SuppressWarnings("unchecked")
    public static <T> T getL2Cache(
            RedisService redisService,
            String key,
            TypeReference<T> typeReference,
            Cache<String, Object> caffeineCache) {
        T result = (T) caffeineCache.getIfPresent(key);
        if (result != null) {
            log.info("Read local cache, key={}", key);
            return result;
        }

        result = redisService.getCacheObject(key, typeReference);
        if (result != null) {
            log.info("Read Redis cache, key={}", key);
            caffeineCache.put(key, result);
            return result;
        }

        log.info("Cache missed, key={}", key);
        return null;
    }

    /**
     * 设置两级缓存数据。
     *
     * <p>先写入 Redis，再写入本地缓存。Redis 写入失败时异常会向外抛出，
     * 避免出现本地缓存已更新但 Redis 未更新的数据不一致问题。</p>
     *
     * @param redisService Redis 服务
     * @param key 缓存 key
     * @param value 缓存对象值
     * @param caffeineCache 本地缓存服务
     * @param timeout Redis 过期时间
     * @param timeUnit 过期时间单位
     * @param <T> 缓存对象类型
     */
    public static <T> void setL2Cache(
            RedisService redisService,
            String key,
            T value,
            Cache<String, Object> caffeineCache,
            Long timeout,
            TimeUnit timeUnit) {
        redisService.setCacheObject(key, value, timeout, timeUnit);
        log.info("Update Redis cache, key={}", key);

        caffeineCache.put(key, value);
        log.info("Update local cache, key={}", key);
    }

    /**
     * 删除两级缓存数据。
     *
     * <p>写库成功后优先删除缓存，让下一次读取重新从数据库加载，避免缓存中保留旧数据。</p>
     *
     * @param redisService Redis 服务
     * @param key 缓存 key
     * @param caffeineCache 本地缓存服务
     */
    public static void deleteL2Cache(
            RedisService redisService,
            String key,
            Cache<String, Object> caffeineCache) {
        redisService.deleteObject(key);
        log.info("Delete Redis cache, key={}", key);

        caffeineCache.invalidate(key);
        log.info("Delete local cache, key={}", key);
    }
}
