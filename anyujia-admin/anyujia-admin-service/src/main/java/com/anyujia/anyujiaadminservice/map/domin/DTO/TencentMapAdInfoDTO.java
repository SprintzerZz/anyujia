package com.anyujia.anyujiaadminservice.map.domin.DTO;

import lombok.Data;

/**
 * 腾讯位置服务行政区划信息 DTO。
 */
@Data
public class TencentMapAdInfoDTO {

    /**
     * 行政区划编码。
     */
    private String adcode;

    /**
     * 省份名称。
     */
    private String province;

    /**
     * 城市名称。
     */
    private String city;

    /**
     * 区县名称。
     */
    private String district;
}
