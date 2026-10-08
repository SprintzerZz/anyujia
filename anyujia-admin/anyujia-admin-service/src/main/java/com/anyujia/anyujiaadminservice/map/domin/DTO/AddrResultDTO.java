package com.anyujia.anyujiaadminservice.map.domin.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 腾讯位置服务逆地址解析结果 DTO。
 */
@Data
public class AddrResultDTO {

    /**
     * 格式化后的地址。
     */
    private String address;

    /**
     * 坐标信息。
     */
    private LocationDTO location;

    /**
     * 行政区划信息。
     */
    @JsonProperty("ad_info")
    private TencentMapAdInfoDTO adInfo;
}
