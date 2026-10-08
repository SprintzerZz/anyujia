package com.anyujia.anyujiaadminservice.map.domin.DTO;

import lombok.Data;

/**
 * 坐标信息 DTO。
 */
@Data
public class LocationDTO {

    /**
     * 纬度。
     */
    private Double lat;

    /**
     * 经度。
     */
    private Double lng;

    /**
     * 格式化为腾讯位置服务需要的 location 参数。
     *
     * @return 纬度,经度
     */
    public String formatInfo() {
        return lat + "," + lng;
    }
}
