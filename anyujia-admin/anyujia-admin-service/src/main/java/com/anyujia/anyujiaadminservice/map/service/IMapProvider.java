package com.anyujia.anyujiaadminservice.map.service;

import com.anyujia.anyujiaadminservice.map.domin.DTO.GeoResultDTO;
import com.anyujia.anyujiaadminservice.map.domin.DTO.LocationDTO;
import com.anyujia.anyujiaadminservice.map.domin.DTO.PoiListDTO;
import com.anyujia.anyujiaadminservice.map.domin.DTO.SuggestSearchDTO;

/**
 * 地图服务提供者。
 */
public interface IMapProvider {

    /**
     * 根据关键词搜索地点。
     *
     * @param suggestSearchDTO 搜索条件
     * @return 搜索结果
     */
    PoiListDTO searchTencentMapPlaceByRegion(SuggestSearchDTO suggestSearchDTO);

    /**
     * 根据经纬度获取区域信息。
     *
     * @param locationDTO 经纬度
     * @return 区域信息
     */
    GeoResultDTO getTencentMapDistrictByLonLat(LocationDTO locationDTO);
}
