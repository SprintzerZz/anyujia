package com.anyujia.anyujiaadminservice.map.domin.DTO;

import lombok.Data;

/**
 * 腾讯位置服务地点 POI 信息 DTO。
 */
@Data
public class PoiDTO {

    /**
     * POI 地点唯一标识。
     */
    private String id;

    /**
     * POI 地点名称。
     */
    private String title;

    /**
     * POI 地点地址。
     */
    private String address;

    /**
     * POI 类型。
     *
     * <p>取值说明：0-普通 POI，1-公交车站，2-地铁站，3-公交线路，4-行政区划。</p>
     */
    private String type;

    /**
     * POI 地点坐标。
     */
    private LocationDTO location;
}
