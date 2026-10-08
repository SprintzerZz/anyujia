package com.anyujia.anyujiacommonredis.service;

import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * Redisson 分布式锁服务。
 *
 * <p>该服务基于 Redisson 的 {@link RLock} 实现分布式锁，适用于定时任务防重、
 * 防止重复提交、跨实例串行处理同一业务资源等场景。</p>
 *
 * <p>锁 key 建议使用固定业务前缀加唯一业务标识，例如
 * {@code lock:order:pay:10001}，避免不同业务互相影响。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class RedissonLockService {

    /**
     * Redisson 操作客户端。
     */
    private final RedissonClient redissonClient;

    /**
     * 获取分布式锁。
     *
     * <p>当 {@code expire > 0} 时，锁会在指定毫秒数后自动释放，不会触发 Redisson 看门狗续期。
     * 当 {@code expire == -1} 时，使用 Redisson 默认看门狗机制自动续期，直到业务主动释放锁。</p>
     *
     * @param lockKey 锁 key，唯一标识，建议使用“模块名 + 业务唯一键”
     * @param expire 锁持有时间，单位毫秒；传入 -1 表示启用 Redisson 看门狗自动续期
     * @return 获取到的锁实例；返回 null 表示加锁失败
     */
    public RLock acquire(String lockKey, long expire) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            if (expire == -1) {
                lock.lock();
            } else {
                lock.lock(expire, TimeUnit.MILLISECONDS);
            }
            return lock;
        } catch (Exception ex) {
            log.warn("Acquire distributed lock failed, lockKey={}, expire={}", lockKey, expire, ex);
            return null;
        }
    }

    /**
     * 尝试获取分布式锁。
     *
     * <p>该方法适合逻辑过期缓存重建场景：获取到锁的线程负责重建缓存，未获取到锁的线程直接返回旧缓存，
     * 避免热点 key 过期后大量请求阻塞或同时打到数据库。</p>
     *
     * @param lockKey 锁 key，唯一标识
     * @param waitTime 等待时间
     * @param leaseTime 锁自动释放时间
     * @param timeUnit 时间单位
     * @return 获取到的锁实例；返回 null 表示未获取到锁
     */
    public RLock tryAcquire(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            if (lock.tryLock(waitTime, leaseTime, timeUnit)) {
                return lock;
            }
        } catch (Exception ex) {
            log.warn("Try acquire distributed lock failed, lockKey={}, waitTime={}, leaseTime={}",
                    lockKey, waitTime, leaseTime, ex);
        }
        return null;
    }

    /**
     * 释放分布式锁。
     *
     * <p>Redisson 要求加锁和解锁必须在同一个线程中完成。释放前会先检查当前线程是否持有该锁，
     * 防止误释放其他线程或其他实例持有的锁。</p>
     *
     * @param lock 锁实例，来自 {@link #acquire(String, long)} 的返回值
     * @return true 表示释放成功；false 表示锁为空、当前线程未持有锁或释放失败
     */
    public boolean releaseLock(RLock lock) {
        if (lock == null) {
            return false;
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                return true;
            }
        } catch (Exception ex) {
            log.warn("Release distributed lock failed, lockName={}", lock.getName(), ex);
        }
        return false;
    }
}
