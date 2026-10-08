package com.anyujia.anyujiaadminservice.map.domin.DTO;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 腾讯位置服务 POI 搜索响应 DTO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PoiListDTO extends TencentMapBaseResponseDTO {

    /**
     * 本次搜索结果数量。
     */
    private Integer count;

    /**
     * POI 搜索结果列表。
     */
    private List<PoiDTO> data;
}
