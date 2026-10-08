package com.anyujia.anyujiaadminapi.map.feign;

import java.util.List;
import java.util.Map;

import com.anyujia.anyujiaadminapi.map.domin.dto.LocationReqDTO;
import com.anyujia.anyujiaadminapi.map.domin.dto.PlaceSearchReqDTO;
import com.anyujia.anyujiaadminapi.map.domin.vo.RegionCityVO;
import com.anyujia.anyujiaadminapi.map.domin.vo.RegionVO;
import com.anyujia.anyujiaadminapi.map.domin.vo.SearchPoiVO;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 地图服务 Feign 客户端。
 */
@FeignClient(contextId = "mapFeignClient", value = "anyujia-admin")
public interface MapFeignClient {

    /**
     * 查询城市列表。
     *
     * @return 城市列表信息
     */
    @GetMapping("/map/city_list")
    ApiResult<List<RegionVO>> getCityList();

    /**
     * 查询按拼音首字母分组的城市列表。
     *
     * @return 按 A-Z 分组的城市列表信息
     */
    @GetMapping("/map/city_pinyin_list")
    ApiResult<Map<String, List<RegionVO>>> getCityPylist();

    /**
     * 查询热门城市列表。
     *
     * @return 热门城市列表信息
     */
    @GetMapping("/map/city_hot_list")
    ApiResult<List<RegionVO>> getHotCityList();

    /**
     * 根据父级区域 ID 查询下级行政区划列表。
     *
     * @param parentId 父级区域 ID
     * @return 下级行政区划列表
     */
    @GetMapping("/map/region_children_list")
    ApiResult<List<RegionVO>> regionChildren(@RequestParam("parentId") Long parentId);

    /**
     * 根据关键词搜索地图地点。
     *
     * @param placeSearchReqDTO 地点搜索条件
     * @return 分页地点搜索结果
     */
    @PostMapping("/map/search")
    ApiResult<BasePageVO<SearchPoiVO>> searchSuggestOnMap(@RequestBody PlaceSearchReqDTO placeSearchReqDTO);

    /**
     * 根据经纬度定位城市。
     *
     * @param locationReqDTO 经纬度信息
     * @return 城市信息
     */
    @PostMapping("/map/locate_city_by_location")
    ApiResult<RegionCityVO> locateCityByLocation(@RequestBody LocationReqDTO locationReqDTO);
}
