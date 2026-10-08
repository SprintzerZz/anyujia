package com.anyujia.anyujiaadminservice.map.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.anyujia.anyujiaadminapi.map.domin.dto.LocationReqDTO;
import com.anyujia.anyujiaadminapi.map.domin.dto.PlaceSearchReqDTO;
import com.anyujia.anyujiaadminapi.map.domin.vo.RegionCityVO;
import com.anyujia.anyujiaadminapi.map.domin.vo.RegionVO;
import com.anyujia.anyujiaadminapi.map.domin.vo.SearchPoiVO;
import com.anyujia.anyujiaadminapi.map.feign.MapFeignClient;
import com.anyujia.anyujiaadminservice.map.domin.DTO.SysRegionDTO;
import com.anyujia.anyujiaadminservice.map.service.IMapService;
import com.anyujia.anyujiacommoncore.utils.BeanCopyUtil;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * 地图管理接口。
 */
@RestController
@RequiredArgsConstructor
public class MapController implements MapFeignClient {

    /**
     * 地图服务。
     */
    private final IMapService mapService;

    /**
     * 城市列表查询。
     *
     * @return 城市列表信息
     */
    @Override
    public ApiResult<List<RegionVO>> getCityList() {
        List<SysRegionDTO> regionDTOS = mapService.getCityList();
        List<RegionVO> result = BeanCopyUtil.copyListProperties(regionDTOS, RegionVO::new);
        return ApiResult.success(result);
    }

    /**
     * 查询按拼音首字母分组的城市列表。
     *
     * @return 按 A-Z 分组的城市列表信息
     */
    @Override
    public ApiResult<Map<String, List<RegionVO>>> getCityPylist() {
        Map<String, List<SysRegionDTO>> cityPylist = mapService.getCityPylist();
        Map<String, List<RegionVO>> result = new LinkedHashMap<>(cityPylist.size());
        cityPylist.forEach((key, value) ->
                result.put(key, BeanCopyUtil.copyListProperties(value, RegionVO::new)));
        return ApiResult.success(result);
    }

    /**
     * 热门城市列表查询。
     *
     * @return 热门城市列表信息
     */
    @Override
    public ApiResult<List<RegionVO>> getHotCityList() {
        List<SysRegionDTO> regionDTOS = mapService.getHotCityList();
        List<RegionVO> result = BeanCopyUtil.copyListProperties(regionDTOS, RegionVO::new);
        return ApiResult.success(result);
    }

    /**
     * 根据父级区域 ID 查询下级行政区划列表。
     *
     * @param parentId 父级区域 ID
     * @return 下级行政区划列表
     */
    @Override
    public ApiResult<List<RegionVO>> regionChildren(Long parentId) {
        List<SysRegionDTO> regionDTOS = mapService.regionChildren(parentId);
        List<RegionVO> result = BeanCopyUtil.copyListProperties(regionDTOS, RegionVO::new);
        return ApiResult.success(result);
    }

    /**
     * 根据关键词搜索地图地点。
     *
     * @param placeSearchReqDTO 地点搜索条件
     * @return 分页地点搜索结果
     */
    @Override
    public ApiResult<BasePageVO<SearchPoiVO>> searchSuggestOnMap(PlaceSearchReqDTO placeSearchReqDTO) {
        return ApiResult.success(mapService.searchSuggestOnMap(placeSearchReqDTO));
    }

    /**
     * 根据经纬度定位城市。
     *
     * @param locationReqDTO 经纬度信息
     * @return 城市信息
     */
    @Override
    public ApiResult<RegionCityVO> locateCityByLocation(LocationReqDTO locationReqDTO) {
        return ApiResult.success(mapService.locateCityByLocation(locationReqDTO));
    }
}
