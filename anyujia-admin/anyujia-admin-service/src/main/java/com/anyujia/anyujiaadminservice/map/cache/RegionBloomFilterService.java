package com.anyujia.anyujiaadminservice.map.cache;

import java.util.List;

import com.anyujia.anyujiaadminservice.map.config.MapCacheProperties;
import com.anyujia.anyujiaadminservice.map.domin.entity.SysRegion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 行政区划编码 BloomFilter 服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionBloomFilterService {

    /**
     * Redisson 客户端，未启用 Redisson 时允许为空。
     */
    private final ObjectProvider<RedissonClient> redissonClientProvider;

    /**
     * 地图缓存配置。
     */
    private final MapCacheProperties mapCacheProperties;

    /**
     * 重建行政区划编码 BloomFilter。
     *
     * @param regions 全量行政区划数据
     */
    public void rebuild(List<SysRegion> regions) {
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient == null || regions == null) {
            return;
        }

        try {
            RBloomFilter<String> bloomFilter = initBloomFilter(redissonClient);
            for (SysRegion region : regions) {
                if (region != null && StringUtils.hasText(region.getCode())) {
                    bloomFilter.add(region.getCode());
                }
            }
            log.info("Rebuild region bloom filter finished, key={}, size={}",
                    mapCacheProperties.getRegionBloomKey(), regions.size());
        } catch (Exception ex) {
            log.warn("Rebuild region bloom filter failed, key={}", mapCacheProperties.getRegionBloomKey(), ex);
        }
    }

    /**
     * 判断行政区划编码是否可能存在。
     *
     * @param code 行政区划编码
     * @return true 表示可能存在；false 表示一定不存在
     */
    public boolean mightContainCode(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }

        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient == null) {
            return true;
        }
        try {
            return redissonClient.getBloomFilter(mapCacheProperties.getRegionBloomKey()).contains(code);
        } catch (Exception ex) {
            log.warn("Check region bloom filter failed, key={}, code={}",
                    mapCacheProperties.getRegionBloomKey(), code, ex);
            return true;
        }
    }

    /**
     * 初始化 BloomFilter，配置变化时清理历史配置后重试。
     *
     * @param redissonClient Redisson 客户端
     * @return BloomFilter 实例
     */
    private RBloomFilter<String> initBloomFilter(RedissonClient redissonClient) {
        String bloomKey = mapCacheProperties.getRegionBloomKey();
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(bloomKey);
        bloomFilter.delete();
        try {
            bloomFilter.tryInit(
                    mapCacheProperties.getRegionBloomExpectedInsertions(),
                    mapCacheProperties.getRegionBloomFalseProbability());
            return bloomFilter;
        } catch (Exception ex) {
            log.warn("Region bloom filter config changed, delete config key and retry, key={}", bloomKey, ex);
            redissonClient.getKeys().delete(bloomKey, bloomKey + ":config", "{" + bloomKey + "}:config");
            bloomFilter = redissonClient.getBloomFilter(bloomKey);
            bloomFilter.tryInit(
                    mapCacheProperties.getRegionBloomExpectedInsertions(),
                    mapCacheProperties.getRegionBloomFalseProbability());
            return bloomFilter;
        }
    }
}
