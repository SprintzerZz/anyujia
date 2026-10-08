package com.anyujia.anyujiaadminapi.map.domin.dto;

import lombok.Data;

/**
 * 经纬度定位请求参数。
 */
@Data
public class LocationReqDTO {

    /**
     * 纬度。
     */
    private Double latitude;

    /**
     * 经度。
     */
    private Double longitude;
}
