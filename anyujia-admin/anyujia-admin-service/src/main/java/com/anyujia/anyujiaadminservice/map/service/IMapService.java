package com.anyujia.anyujiaadminservice.map.service;

import java.util.List;
import java.util.Map;

import com.anyujia.anyujiaadminapi.map.domin.dto.LocationReqDTO;
import com.anyujia.anyujiaadminapi.map.domin.dto.PlaceSearchReqDTO;
import com.anyujia.anyujiaadminapi.map.domin.vo.RegionCityVO;
import com.anyujia.anyujiaadminapi.map.domin.vo.SearchPoiVO;
import com.anyujia.anyujiaadminservice.map.domin.DTO.SysRegionDTO;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;

/**
 * 地图服务。
 */
public interface IMapService {

    /**
     * 查询城市列表。
     *
     * @return 城市列表
     */
    List<SysRegionDTO> getCityList();

    /**
     * 获取按照 A-Z 分开的城市列表信息。
     *
     * @return 按拼音首字母分组的城市列表
     */
    Map<String, List<SysRegionDTO>> getCityPylist();

    /**
     * 获取热门城市列表。
     *
     * @return 热门城市列表
     */
    List<SysRegionDTO> getHotCityList();

    /**
     * 根据父级区域 ID 查询下级行政区划列表。
     *
     * @param parentId 父级区域 ID
     * @return 下级行政区划列表
     */
    List<SysRegionDTO> regionChildren(Long parentId);

    /**
     * 根据关键词搜索地图地点。
     *
     * @param placeSearchReqDTO 地点搜索条件
     * @return 分页地点搜索结果
     */
    BasePageVO<SearchPoiVO> searchSuggestOnMap(PlaceSearchReqDTO placeSearchReqDTO);

    /**
     * 根据经纬度定位城市。
     *
     * @param locationReqDTO 经纬度信息
     * @return 城市信息
     */
    RegionCityVO locateCityByLocation(LocationReqDTO locationReqDTO);

    /**
     * 事务提交后清理城市相关缓存。
     */
    void clearCityCacheAfterCommit();
}
