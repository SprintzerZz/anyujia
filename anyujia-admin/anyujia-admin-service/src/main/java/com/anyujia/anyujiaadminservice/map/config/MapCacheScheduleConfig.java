package com.anyujia.anyujiaadminservice.map.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 地图缓存定时任务配置。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "map.cache", name = "consistency-check-enabled", havingValue = "true")
public class MapCacheScheduleConfig {
}
