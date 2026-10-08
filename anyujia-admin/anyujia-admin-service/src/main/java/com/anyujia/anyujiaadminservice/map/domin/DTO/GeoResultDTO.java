package com.anyujia.anyujiaadminservice.map.domin.DTO;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 腾讯位置服务逆地址解析响应 DTO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GeoResultDTO extends TencentMapBaseResponseDTO {

    /**
     * 逆地址解析结果信息。
     */
    private AddrResultDTO result;
}
