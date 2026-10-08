package com.anyujia.anyujiaadminapi.map.constants;

/**
 * 地图模块业务常量。
 */
public final class MapConstants {

    private MapConstants() {
    }

    /**
     * 城市层级，sys_region.level 中市级为 2。
     */
    public static final Integer CITY_LEVEL = 2;

    /**
     * 热门城市系统参数 key，对应 sys_argument.config_key，参数值为 sys_region.id 列表。
     */
    public static final String HOT_CITY_ARGUMENT_KEY = "sys_hot_city";

    /**
     * 腾讯位置服务关键词输入提示接口。
     */
    public static final String TENCENT_MAP_API_PLACE_SUGGESTION = "/ws/place/v1/suggestion";

    /**
     * 腾讯位置服务逆地址解析接口。
     */
    public static final String TENCENT_MAP_GEOCODER = "/ws/geocoder/v1/";
}
