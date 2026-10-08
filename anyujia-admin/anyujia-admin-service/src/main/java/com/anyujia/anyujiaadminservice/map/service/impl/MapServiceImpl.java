package com.anyujia.anyujiaadminservice.map.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentDTO;
import com.anyujia.anyujiaadminapi.map.constants.MapConstants;
import com.anyujia.anyujiaadminapi.map.domin.dto.LocationReqDTO;
import com.anyujia.anyujiaadminapi.map.domin.dto.PlaceSearchReqDTO;
import com.anyujia.anyujiaadminapi.map.domin.vo.RegionCityVO;
import com.anyujia.anyujiaadminapi.map.domin.vo.SearchPoiVO;
import com.anyujia.anyujiaadminservice.config.service.ISysArgumentService;
import com.anyujia.anyujiaadminservice.map.cache.MapCacheInvalidationMessage;
import com.anyujia.anyujiaadminservice.map.cache.RegionBloomFilterService;
import com.anyujia.anyujiaadminservice.map.config.MapCacheProperties;
import com.anyujia.anyujiaadminservice.map.domin.DTO.GeoResultDTO;
import com.anyujia.anyujiaadminservice.map.domin.DTO.LocationDTO;
import com.anyujia.anyujiaadminservice.map.domin.DTO.PoiDTO;
import com.anyujia.anyujiaadminservice.map.domin.DTO.PoiListDTO;
import com.anyujia.anyujiaadminservice.map.domin.DTO.SuggestSearchDTO;
import com.anyujia.anyujiaadminservice.map.domin.DTO.SysRegionDTO;
import com.anyujia.anyujiaadminservice.map.domin.DTO.TencentMapAdInfoDTO;
import com.anyujia.anyujiaadminservice.map.domin.entity.SysRegion;
import com.anyujia.anyujiaadminservice.map.mapper.RegionMapper;
import com.anyujia.anyujiaadminservice.map.service.IMapProvider;
import com.anyujia.anyujiaadminservice.map.service.IMapService;
import com.anyujia.anyujiacommoncache.domain.CacheData;
import com.anyujia.anyujiacommoncache.utils.CacheUtil;
import com.anyujia.anyujiacommonrabbitmq.service.RabbitBroadcastService;
import com.anyujia.anyujiacommonredis.service.RedisService;
import com.anyujia.anyujiacommonredis.service.RedissonLockService;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 地图服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MapServiceImpl implements IMapService {

    private static final String REGION_CACHE_INVALIDATE_EVENT = "REGION_CACHE_INVALIDATE";

    private static final String CACHE_SCOPE_CITY = "CITY";

    /**
     * 行政区划 Mapper。
     */
    private final RegionMapper regionMapper;

    /**
     * Redis 缓存服务。
     */
    private final RedisService redisService;

    /**
     * Caffeine 本地缓存服务。
     */
    private final Cache<String, Object> caffeineCache;

    /**
     * 地图缓存配置。
     */
    private final MapCacheProperties mapCacheProperties;

    /**
     * 系统参数服务，用于读取热门城市等业务配置。
     */
    private final ISysArgumentService sysArgumentService;

    /**
     * 行政区划 BloomFilter 服务。
     */
    private final RegionBloomFilterService regionBloomFilterService;

    /**
     * Redisson 分布式锁服务，未创建 RedissonClient 时允许为空。
     */
    private final ObjectProvider<RedissonLockService> redissonLockServiceProvider;

    /**
     * RabbitMQ 广播服务，未开启广播时允许为空。
     */
    private final ObjectProvider<RabbitBroadcastService> rabbitBroadcastServiceProvider;

    /**
     * 地图厂商服务提供者，未启用地图厂商时允许为空。
     */
    private final ObjectProvider<IMapProvider> mapProviderProvider;

    /**
     * 应用启动完成后预热城市相关缓存。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initCityMap() {
        try {
            forceRebuildCityCacheWithLock();
        } catch (Exception ex) {
            log.warn("Init city cache failed", ex);
        }
    }

    /**
     * 查询城市列表。
     *
     * @return 城市列表
     */
    @Override
    public List<SysRegionDTO> getCityList() {
        CacheData<List<SysRegionDTO>> cacheData = getCityListCacheData();
        if (cacheData == null) {
            return forceRebuildCityCacheWithLock();
        }
        rebuildIfLogicalExpired(cacheData);
        return cacheData.getData();
    }

    /**
     * 获取按照 A-Z 分开的城市列表信息。
     *
     * @return 按拼音首字母分组的城市列表
     */
    @Override
    public Map<String, List<SysRegionDTO>> getCityPylist() {
        CacheData<Map<String, List<SysRegionDTO>>> cacheData = getCityPinyinCacheData();
        if (cacheData == null) {
            forceRebuildCityCacheWithLock();
            cacheData = getCityPinyinCacheData();
        }
        if (cacheData == null) {
            return Collections.emptyMap();
        }
        rebuildIfLogicalExpired(cacheData);
        return cacheData.getData();
    }

    /**
     * 获取热门城市列表。
     *
     * @return 热门城市列表
     */
    @Override
    public List<SysRegionDTO> getHotCityList() {
        CacheData<List<SysRegionDTO>> cacheData = getHotCityListCacheData();
        if (cacheData == null) {
            forceRebuildCityCacheWithLock();
            cacheData = getHotCityListCacheData();
        }
        if (cacheData == null) {
            return Collections.emptyList();
        }
        rebuildIfLogicalExpired(cacheData);
        return cacheData.getData();
    }

    /**
     * 事务提交后清理城市相关缓存。
     */
    /**
     * 根据父级区域 ID 查询下级行政区划列表。
     *
     * @param parentId 父级区域 ID
     * @return 下级行政区划列表
     */
    @Override
    public List<SysRegionDTO> regionChildren(Long parentId) {
        List<SysRegion> regions = regionMapper.selectList(new LambdaQueryWrapper<SysRegion>()
                .eq(SysRegion::getParentId, parentId)
                .orderByAsc(SysRegion::getPinyin));
        List<SysRegionDTO> result = new ArrayList<>(regions.size());
        for (SysRegion region : regions) {
            SysRegionDTO regionDTO = new SysRegionDTO();
            BeanUtils.copyProperties(region, regionDTO);
            result.add(regionDTO);
        }
        return result;
    }

    /**
     * 根据关键词搜索地图地点。
     *
     * @param placeSearchReqDTO 地点搜索条件
     * @return 分页地点搜索结果
     */
    @Override
    public BasePageVO<SearchPoiVO> searchSuggestOnMap(PlaceSearchReqDTO placeSearchReqDTO) {
        IMapProvider mapProvider = getMapProvider();
        SuggestSearchDTO suggestSearchDTO = new SuggestSearchDTO();
        suggestSearchDTO.setId(placeSearchReqDTO.getId());
        suggestSearchDTO.setPageIndex(placeSearchReqDTO.getPageIndex());
        suggestSearchDTO.setPageSize(placeSearchReqDTO.getPageSize());
        suggestSearchDTO.setKeyword(placeSearchReqDTO.getKeyword());

        PoiListDTO poiListDTO = mapProvider.searchTencentMapPlaceByRegion(suggestSearchDTO);
        BasePageVO<SearchPoiVO> pageVO = new BasePageVO<>();
        pageVO.setPageIndex(placeSearchReqDTO.getPageIndex());
        pageVO.setPageSize(placeSearchReqDTO.getPageSize());
        pageVO.setTotal(poiListDTO.getCount() == null ? 0L : poiListDTO.getCount().longValue());
        pageVO.setList(buildSearchPoiVOList(poiListDTO.getData()));
        return pageVO;
    }

    /**
     * 根据经纬度定位城市。
     *
     * @param locationReqDTO 经纬度信息
     * @return 城市信息
     */
    @Override
    public RegionCityVO locateCityByLocation(LocationReqDTO locationReqDTO) {
        IMapProvider mapProvider = getMapProvider();
        LocationDTO locationDTO = new LocationDTO();
        locationDTO.setLat(locationReqDTO.getLatitude());
        locationDTO.setLng(locationReqDTO.getLongitude());

        GeoResultDTO geoResultDTO = mapProvider.getTencentMapDistrictByLonLat(locationDTO);
        if (geoResultDTO == null || geoResultDTO.getResult() == null
                || geoResultDTO.getResult().getAdInfo() == null) {
            throw new ServiceException(ResultCode.TENCENT_MAP_LOCATE_FAILED);
        }

        TencentMapAdInfoDTO adInfo = geoResultDTO.getResult().getAdInfo();
        SysRegionDTO city = findCityByTencentAdInfo(adInfo);
        if (city == null) {
            throw new ServiceException(ResultCode.TENCENT_MAP_CITY_UNKNOWN);
        }

        RegionCityVO regionCityVO = new RegionCityVO();
        regionCityVO.setCode(city.getCode());
        regionCityVO.setName(city.getName());
        regionCityVO.setFullName(city.getFullName());
        regionCityVO.setAddress(geoResultDTO.getResult().getAddress());
        return regionCityVO;
    }

    @Override
    public void clearCityCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clearCityCacheAndBroadcast();
                }
            });
            return;
        }

        clearCityCacheAndBroadcast();
    }

    /**
     * 从两级缓存读取城市列表包装数据。
     *
     * @return 城市列表缓存
     */
    /**
     * 获取已启用的地图厂商服务。
     *
     * @return 地图厂商服务
     */
    private IMapProvider getMapProvider() {
        IMapProvider mapProvider = mapProviderProvider.getIfAvailable();
        if (mapProvider == null) {
            throw new ServiceException(ResultCode.MAP_NOT_ENABLED);
        }
        return mapProvider;
    }

    /**
     * 将腾讯地图 POI 响应转换成接口响应 VO。
     *
     * @param poiList POI 响应列表
     * @return 地点搜索响应列表
     */
    private List<SearchPoiVO> buildSearchPoiVOList(List<PoiDTO> poiList) {
        if (poiList == null || poiList.isEmpty()) {
            return Collections.emptyList();
        }

        List<SearchPoiVO> result = new ArrayList<>(poiList.size());
        for (PoiDTO poiDTO : poiList) {
            SearchPoiVO searchPoiVO = new SearchPoiVO();
            searchPoiVO.setTitle(poiDTO.getTitle());
            searchPoiVO.setAddress(poiDTO.getAddress());
            if (poiDTO.getLocation() != null) {
                searchPoiVO.setLongitude(poiDTO.getLocation().getLng());
                searchPoiVO.setLatitude(poiDTO.getLocation().getLat());
            }
            result.add(searchPoiVO);
        }
        return result;
    }

    /**
     * 根据腾讯逆地址解析行政区信息匹配本地城市。
     *
     * @param adInfo 腾讯地图行政区信息
     * @return 本地城市信息
     */
    private SysRegionDTO findCityByTencentAdInfo(TencentMapAdInfoDTO adInfo) {
        String cityName = StringUtils.hasText(adInfo.getCity()) ? adInfo.getCity() : adInfo.getProvince();
        if (!StringUtils.hasText(cityName)) {
            return null;
        }

        for (SysRegionDTO city : getCityList()) {
            if (cityName.equals(city.getName()) || cityName.equals(city.getFullName())) {
                return city;
            }
        }
        return null;
    }

    private CacheData<List<SysRegionDTO>> getCityListCacheData() {
        return CacheUtil.getL2Cache(
                redisService,
                mapCacheProperties.getCityListKey(),
                new TypeReference<CacheData<List<SysRegionDTO>>>() {
                },
                caffeineCache);
    }

    /**
     * 从两级缓存读取拼音分组城市包装数据。
     *
     * @return 拼音分组缓存
     */
    private CacheData<Map<String, List<SysRegionDTO>>> getCityPinyinCacheData() {
        return CacheUtil.getL2Cache(
                redisService,
                mapCacheProperties.getCityPinyinKey(),
                new TypeReference<CacheData<Map<String, List<SysRegionDTO>>>>() {
                },
                caffeineCache);
    }

    /**
     * 从两级缓存读取热门城市包装数据。
     *
     * @return 热门城市缓存
     */
    private CacheData<List<SysRegionDTO>> getHotCityListCacheData() {
        return CacheUtil.getL2Cache(
                redisService,
                mapCacheProperties.getCityHotKey(),
                new TypeReference<CacheData<List<SysRegionDTO>>>() {
                },
                caffeineCache);
    }

    /**
     * 逻辑过期后尝试获取锁重建缓存，未拿到锁时返回旧值。
     *
     * @param cacheData 缓存包装数据
     */
    private void rebuildIfLogicalExpired(CacheData<?> cacheData) {
        if (cacheData == null || !cacheData.isLogicalExpired()) {
            return;
        }

        RedissonLockService lockService = redissonLockServiceProvider.getIfAvailable();
        if (lockService == null) {
            return;
        }

        RLock lock = lockService.tryAcquire(
                mapCacheProperties.getCityReloadLockKey(),
                mapCacheProperties.getCityReloadLockWaitMillis(),
                mapCacheProperties.getCityReloadLockExpireMillis(),
                TimeUnit.MILLISECONDS);
        if (lock == null) {
            log.info("City cache logical expired, return stale cache because rebuild lock not acquired");
            return;
        }

        try {
            forceRebuildCityCache();
        } finally {
            lockService.releaseLock(lock);
        }
    }

    /**
     * 使用分布式锁强制重建城市缓存。
     *
     * @return 城市列表
     */
    private List<SysRegionDTO> forceRebuildCityCacheWithLock() {
        RedissonLockService lockService = redissonLockServiceProvider.getIfAvailable();
        RLock lock = null;
        try {
            if (lockService != null) {
                lock = lockService.acquire(
                        mapCacheProperties.getCityReloadLockKey(),
                        mapCacheProperties.getCityReloadLockExpireMillis());
            }
            return forceRebuildCityCache();
        } finally {
            if (lockService != null) {
                lockService.releaseLock(lock);
            }
        }
    }

    /**
     * 强制从数据库重建城市相关缓存。
     *
     * @return 城市列表
     */
    private List<SysRegionDTO> forceRebuildCityCache() {
        Long rebuildVersion = getCityCacheVersion();
        List<SysRegion> list = regionMapper.selectAllRegion();
        if (!rebuildVersion.equals(getCityCacheVersion())) {
            log.info("Skip stale city cache rebuild, rebuildVersion={}, currentVersion={}",
                    rebuildVersion, getCityCacheVersion());
            CacheData<List<SysRegionDTO>> cacheData = getCityListCacheData();
            return cacheData == null ? Collections.emptyList() : cacheData.getData();
        }

        List<SysRegionDTO> cityList = buildCityList(list);
        regionBloomFilterService.rebuild(list);
        loadCityInfo(cityList, rebuildVersion);
        loadCityPinyinInfo(cityList, rebuildVersion);
        loadHotCityInfo(list, rebuildVersion);
        log.info("Rebuild city cache finished, version={}", rebuildVersion);
        return cityList;
    }

    /**
     * 删除 Redis 和当前实例 Caffeine，并通知其他实例删除本地缓存。
     */
    private void clearCityCacheAndBroadcast() {
        Long version = increaseCityCacheVersion();
        List<String> keys = getCityCacheKeys();
        for (String key : keys) {
            CacheUtil.deleteL2Cache(redisService, key, caffeineCache);
        }

        RabbitBroadcastService rabbitBroadcastService = rabbitBroadcastServiceProvider.getIfAvailable();
        if (rabbitBroadcastService != null && Boolean.TRUE.equals(mapCacheProperties.getInvalidateBroadcastEnabled())) {
            MapCacheInvalidationMessage message = new MapCacheInvalidationMessage(
                    UUID.randomUUID().toString(),
                    REGION_CACHE_INVALIDATE_EVENT,
                    CACHE_SCOPE_CITY,
                    null,
                    version,
                    System.currentTimeMillis(),
                    keys);
            rabbitBroadcastService.publishFanout(mapCacheProperties.getInvalidateExchange(), message);
            log.info("Publish map cache invalidation message, keys={}, version={}", keys, version);
        }
    }

    /**
     * 获取城市相关缓存 key。
     *
     * @return 缓存 key 列表
     */
    private List<String> getCityCacheKeys() {
        return List.of(
                mapCacheProperties.getCityListKey(),
                mapCacheProperties.getCityPinyinKey(),
                mapCacheProperties.getCityHotKey());
    }

    /**
     * 缓存城市列表信息。
     *
     * @param cityList 城市列表
     * @param version 缓存版本号
     */
    private void loadCityInfo(List<SysRegionDTO> cityList, Long version) {
        setCacheData(mapCacheProperties.getCityListKey(), cityList, version);
    }

    /**
     * 按拼音首字母缓存城市归类信息。
     *
     * @param cityList 城市列表
     * @param version 缓存版本号
     */
    private void loadCityPinyinInfo(List<SysRegionDTO> cityList, Long version) {
        Map<String, List<SysRegionDTO>> result = new LinkedHashMap<>();
        for (SysRegionDTO city : cityList) {
            String groupKey = getPinyinGroupKey(city.getPinyin());
            result.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(city);
        }
        setCacheData(mapCacheProperties.getCityPinyinKey(), result, version);
    }

    /**
     * 缓存热门城市列表。
     *
     * @param cityList 城市列表
     * @param version 缓存版本号
     */
    private void loadHotCityInfo(List<SysRegion> regionList, Long version) {
        setCacheData(mapCacheProperties.getCityHotKey(), buildHotCityList(regionList), version);
    }

    /**
     * 写入逻辑过期缓存。
     *
     * @param key 缓存 key
     * @param data 缓存数据
     * @param version 缓存版本号
     * @param <T> 缓存数据类型
     */
    private <T> void setCacheData(String key, T data, Long version) {
        CacheData<T> cacheData = new CacheData<>(data, version, getLogicalExpireTime());
        CacheUtil.setL2Cache(
                redisService,
                key,
                cacheData,
                caffeineCache,
                getPhysicalTtl(),
                TimeUnit.SECONDS);
    }

    /**
     * 获取当前城市缓存版本号。
     *
     * @return 版本号
     */
    private Long getCityCacheVersion() {
        Long version = redisService.getCacheObject(mapCacheProperties.getCityVersionKey(), Long.class);
        if (version != null) {
            return version;
        }

        redisService.setCacheObject(mapCacheProperties.getCityVersionKey(), 1L);
        return 1L;
    }

    /**
     * 增加城市缓存版本号。
     *
     * @return 新版本号
     */
    private Long increaseCityCacheVersion() {
        Long version = redisService.increment(mapCacheProperties.getCityVersionKey());
        return version == null ? getCityCacheVersion() : version;
    }

    /**
     * 获取带随机抖动的 Redis 物理过期时间，单位秒。
     *
     * @return Redis TTL 秒数
     */
    private long getPhysicalTtl() {
        long baseTtl = mapCacheProperties.getCityTimeoutUnit().toSeconds(mapCacheProperties.getCityTimeout());
        long jitter = Math.max(mapCacheProperties.getTtlJitterSeconds(), 0L);
        return jitter == 0L ? baseTtl : baseTtl + ThreadLocalRandom.current().nextLong(jitter + 1);
    }

    /**
     * 获取逻辑过期时间。
     *
     * @return 逻辑过期时间
     */
    private LocalDateTime getLogicalExpireTime() {
        long seconds = mapCacheProperties.getCityLogicalTimeoutUnit()
                .toSeconds(mapCacheProperties.getCityLogicalTimeout());
        long jitter = Math.max(mapCacheProperties.getTtlJitterSeconds(), 0L);
        long actualSeconds = jitter == 0L ? seconds : seconds + ThreadLocalRandom.current().nextLong(jitter + 1);
        return LocalDateTime.now().plusSeconds(actualSeconds);
    }

    /**
     * 从全部区划数据中提取城市列表。
     *
     * @param list 全部行政区划数据
     * @return 城市列表
     */
    private List<SysRegionDTO> buildCityList(List<SysRegion> list) {
        List<SysRegionDTO> result = new ArrayList<>();
        for (SysRegion sysRegion : list) {
            if (MapConstants.CITY_LEVEL.equals(sysRegion.getLevel())) {
                SysRegionDTO sysRegionDTO = new SysRegionDTO();
                BeanUtils.copyProperties(sysRegion, sysRegionDTO);
                result.add(sysRegionDTO);
            }
        }
        return result;
    }

    /**
     * 按系统参数中配置的区划 ID 生成热门城市列表。
     *
     * @param regionList 全量行政区划列表
     * @return 热门城市列表
     */
    private List<SysRegionDTO> buildHotCityList(List<SysRegion> regionList) {
        List<Long> hotCityIds = getHotCityIds();
        if (hotCityIds.isEmpty()) {
            log.info("Hot city argument not configured or empty, configKey={}", MapConstants.HOT_CITY_ARGUMENT_KEY);
            return Collections.emptyList();
        }

        Map<Long, SysRegion> regionMap = new LinkedHashMap<>();
        for (SysRegion region : regionList) {
            if (region != null && region.getId() != null) {
                regionMap.put(region.getId(), region);
            }
        }

        List<SysRegionDTO> result = new ArrayList<>();
        for (Long cityId : hotCityIds) {
            SysRegion region = regionMap.get(cityId);
            if (region != null) {
                SysRegionDTO regionDTO = new SysRegionDTO();
                BeanUtils.copyProperties(region, regionDTO);
                result.add(regionDTO);
            }
        }
        log.info("Build hot city cache, configuredIds={}, matchedSize={}",
                hotCityIds, result.size());
        return result;
    }

    /**
     * 从系统参数表读取热门城市 ID。
     * <p>
     * 参数值格式示例：35,108,234,236,289,342。
     *
     * @return 热门城市 ID 列表
     */
    private List<Long> getHotCityIds() {
        ArgumentDTO argumentDTO = sysArgumentService.getByConfigKey(MapConstants.HOT_CITY_ARGUMENT_KEY);
        if (argumentDTO == null || !StringUtils.hasText(argumentDTO.getValue())) {
            return Collections.emptyList();
        }

        List<Long> hotCityIds = new ArrayList<>();
        String[] ids = argumentDTO.getValue().split(",");
        for (String id : ids) {
            String trimmedId = id.trim();
            if (!StringUtils.hasText(trimmedId)) {
                continue;
            }
            try {
                hotCityIds.add(Long.valueOf(trimmedId));
            } catch (NumberFormatException ex) {
                log.warn("Ignore invalid hot city id, configKey={}, value={}", MapConstants.HOT_CITY_ARGUMENT_KEY, trimmedId);
            }
        }
        return hotCityIds;
    }

    /**
     * 获取拼音首字母分组，拼音为空时放入 # 分组。
     *
     * @param pinyin 城市拼音
     * @return 拼音首字母
     */
    private String getPinyinGroupKey(String pinyin) {
        if (!StringUtils.hasText(pinyin)) {
            return "#";
        }
        return pinyin.substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
