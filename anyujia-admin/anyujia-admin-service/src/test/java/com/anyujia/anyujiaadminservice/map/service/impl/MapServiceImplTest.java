package com.anyujia.anyujiaadminservice.map.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentDTO;
import com.anyujia.anyujiaadminapi.map.constants.MapConstants;
import com.anyujia.anyujiaadminservice.config.service.ISysArgumentService;
import com.anyujia.anyujiaadminservice.map.cache.MapCacheInvalidationListener;
import com.anyujia.anyujiaadminservice.map.cache.MapCacheInvalidationMessage;
import com.anyujia.anyujiaadminservice.map.config.MapCacheProperties;
import com.anyujia.anyujiaadminservice.map.cache.RegionBloomFilterService;
import com.anyujia.anyujiaadminservice.map.domin.DTO.SysRegionDTO;
import com.anyujia.anyujiaadminservice.map.domin.entity.SysRegion;
import com.anyujia.anyujiaadminservice.map.mapper.RegionMapper;
import com.anyujia.anyujiacommoncache.domain.CacheData;
import com.anyujia.anyujiacommonredis.service.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MapServiceImplTest {

    @Test
    void getCityListShouldReadL1CacheWhenPresent() {
        TestFixture fixture = createFixture();
        SysRegionDTO city = cityDTO("110000", "Beijing", "beijing");
        fixture.caffeineCache.put(
                fixture.mapCacheProperties.getCityListKey(),
                new CacheData<>(List.of(city), 1L, LocalDateTime.now().plusMinutes(10)));

        List<SysRegionDTO> result = fixture.mapService.getCityList();

        assertThat(result).extracting(SysRegionDTO::getName).containsExactly("Beijing");
        verify(fixture.regionMapper, never()).selectAllRegion();
    }

    @Test
    void getCityPylistShouldGroupCityByPinyinFirstLetterWhenCacheMiss() {
        TestFixture fixture = createFixture();

        Map<String, List<SysRegionDTO>> result = fixture.mapService.getCityPylist();

        assertThat(result).containsOnlyKeys("A", "B");
        assertThat(result.get("A")).extracting(SysRegionDTO::getName).containsExactly("Aba");
        assertThat(result.get("B")).extracting(SysRegionDTO::getName).containsExactly("Beijing");
        verify(fixture.redisService).setCacheObject(
                eq(fixture.mapCacheProperties.getCityPinyinKey()),
                any(CacheData.class),
                eq(TimeUnit.MINUTES.toSeconds(fixture.mapCacheProperties.getCityTimeout())),
                eq(TimeUnit.SECONDS));
    }

    @Test
    void getHotCityListShouldReturnConfiguredCitiesInConfiguredOrder() {
        TestFixture fixture = createFixture();
        ArgumentDTO argumentDTO = new ArgumentDTO();
        argumentDTO.setConfigKey(MapConstants.HOT_CITY_ARGUMENT_KEY);
        argumentDTO.setValue("2,1");
        when(fixture.sysArgumentService.getByConfigKey(MapConstants.HOT_CITY_ARGUMENT_KEY)).thenReturn(argumentDTO);

        List<SysRegionDTO> result = fixture.mapService.getHotCityList();

        assertThat(result).extracting(SysRegionDTO::getName).containsExactly("Beijing", "Aba");
        verify(fixture.redisService).setCacheObject(
                eq(fixture.mapCacheProperties.getCityHotKey()),
                any(CacheData.class),
                eq(TimeUnit.MINUTES.toSeconds(fixture.mapCacheProperties.getCityTimeout())),
                eq(TimeUnit.SECONDS));
    }

    @Test
    void clearCityCacheAfterCommitShouldDeleteCacheOnlyAfterCommit() {
        TestFixture fixture = createFixture();
        TransactionSynchronizationManager.initSynchronization();

        try {
            fixture.mapService.clearCityCacheAfterCommit();
            verify(fixture.redisService, never()).deleteObject(fixture.mapCacheProperties.getCityListKey());

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(fixture.redisService).deleteObject(fixture.mapCacheProperties.getCityListKey());
            verify(fixture.redisService).deleteObject(fixture.mapCacheProperties.getCityPinyinKey());
            verify(fixture.redisService).deleteObject(fixture.mapCacheProperties.getCityHotKey());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void clearCityCacheAfterCommitShouldNotDeleteCacheWhenTransactionRollback() {
        TestFixture fixture = createFixture();
        TransactionSynchronizationManager.initSynchronization();

        try {
            fixture.mapService.clearCityCacheAfterCommit();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(fixture.redisService, never()).deleteObject(fixture.mapCacheProperties.getCityListKey());
        verify(fixture.redisService, never()).deleteObject(fixture.mapCacheProperties.getCityPinyinKey());
        verify(fixture.redisService, never()).deleteObject(fixture.mapCacheProperties.getCityHotKey());
    }

    @Test
    void cacheInvalidationListenerShouldIgnoreDuplicateMessage() {
        Cache<String, Object> caffeineCache = Caffeine.newBuilder().build();
        String cacheKey = "cache:map:city:list:v2";
        caffeineCache.put(cacheKey, "value");
        MapCacheInvalidationListener listener = new MapCacheInvalidationListener(caffeineCache);
        MapCacheInvalidationMessage message = new MapCacheInvalidationMessage(
                "event-1",
                "REGION_CACHE_INVALIDATE",
                "CITY",
                null,
                2L,
                System.currentTimeMillis(),
                List.of(cacheKey));

        listener.onMessage(message);
        listener.onMessage(message);

        assertThat(caffeineCache.getIfPresent(cacheKey)).isNull();
        assertThat(caffeineCache.getIfPresent("cache:map:event:event-1")).isEqualTo(Boolean.TRUE);
    }

    private static TestFixture createFixture() {
        RegionMapper regionMapper = mock(RegionMapper.class);
        RedisService redisService = mock(RedisService.class);
        ISysArgumentService sysArgumentService = mock(ISysArgumentService.class);
        Cache<String, Object> caffeineCache = Caffeine.newBuilder().build();
        MapCacheProperties mapCacheProperties = new MapCacheProperties();
        mapCacheProperties.setTtlJitterSeconds(0L);
        RegionBloomFilterService regionBloomFilterService = mock(RegionBloomFilterService.class);

        when(redisService.getCacheObject(eq(mapCacheProperties.getCityListKey()), any(TypeReference.class)))
                .thenReturn(null);
        when(redisService.getCacheObject(eq(mapCacheProperties.getCityPinyinKey()), any(TypeReference.class)))
                .thenReturn(null);
        when(redisService.getCacheObject(eq(mapCacheProperties.getCityHotKey()), any(TypeReference.class)))
                .thenReturn(null);
        when(redisService.getCacheObject(eq(mapCacheProperties.getCityVersionKey()), eq(Long.class)))
                .thenReturn(1L);
        when(regionMapper.selectAllRegion()).thenReturn(List.of(
                region(1L, "513200", "Aba", "abazangzuqiangzuzizhizhou", 2),
                region(2L, "110000", "Beijing", "beijing", 2),
                region(3L, "510000", "Sichuan", "sichuan", 1)));

        MapServiceImpl mapService = new MapServiceImpl(
                regionMapper,
                redisService,
                caffeineCache,
                mapCacheProperties,
                sysArgumentService,
                regionBloomFilterService,
                emptyProvider(),
                emptyProvider(),
                emptyProvider());
        return new TestFixture(regionMapper, redisService, sysArgumentService, caffeineCache, mapCacheProperties, mapService);
    }

    private static SysRegionDTO cityDTO(String code, String name, String pinyin) {
        SysRegionDTO city = new SysRegionDTO();
        city.setCode(code);
        city.setName(name);
        city.setPinyin(pinyin);
        city.setLevel(2);
        return city;
    }

    private static SysRegion region(Long id, String code, String name, String pinyin, Integer level) {
        SysRegion region = new SysRegion();
        region.setId(id);
        region.setCode(code);
        region.setName(name);
        region.setFullName(name);
        region.setPinyin(pinyin);
        region.setLevel(level);
        return region;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
        ObjectProvider<T> objectProvider = mock(ObjectProvider.class);
        when(objectProvider.getIfAvailable()).thenReturn(null);
        return objectProvider;
    }

    private record TestFixture(
            RegionMapper regionMapper,
            RedisService redisService,
            ISysArgumentService sysArgumentService,
            Cache<String, Object> caffeineCache,
            MapCacheProperties mapCacheProperties,
            MapServiceImpl mapService) {
    }
}
