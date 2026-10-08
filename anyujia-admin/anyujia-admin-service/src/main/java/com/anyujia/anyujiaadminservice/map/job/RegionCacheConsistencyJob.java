package com.anyujia.anyujiaadminservice.map.job;

import com.anyujia.anyujiaadminservice.map.service.IMapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 行政区划缓存一致性兜底校验任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "map.cache", name = "consistency-check-enabled", havingValue = "true")
public class RegionCacheConsistencyJob {

    /**
     * 地图服务。
     */
    private final IMapService mapService;

    /**
     * 定时触发缓存访问，让缺失或逻辑过期的缓存得到修复。
     */
    @Scheduled(cron = "${map.cache.consistency-check-cron:0 0/30 * * * ?}")
    public void checkCityCacheConsistency() {
        try {
            mapService.getCityList();
            mapService.getCityPylist();
            mapService.getHotCityList();
            log.info("Region cache consistency check finished");
        } catch (Exception ex) {
            log.warn("Region cache consistency check failed", ex);
        }
    }
}
