package com.anyujia.anyujiaadminservice.map.service.impl;

import com.anyujia.anyujiaadminapi.map.constants.MapConstants;
import com.anyujia.anyujiaadminservice.map.domin.DTO.GeoResultDTO;
import com.anyujia.anyujiaadminservice.map.domin.DTO.LocationDTO;
import com.anyujia.anyujiaadminservice.map.domin.DTO.PoiListDTO;
import com.anyujia.anyujiaadminservice.map.domin.DTO.SuggestSearchDTO;
import com.anyujia.anyujiaadminservice.map.service.IMapProvider;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 腾讯位置服务提供者实现。
 */
@Slf4j
@Component
@RefreshScope
@RequiredArgsConstructor
@ConditionalOnProperty(value = "map.type", havingValue = "tencent")
public class TencentMapServiceImpl implements IMapProvider {

    /**
     * 腾讯位置服务域名。
     */
    @Value("${tencent.map.api-server}")
    private String apiServer;

    /**
     * 腾讯位置服务调用密钥。
     */
    @Value("${tencent.map.key}")
    private String key;

    /**
     * HTTP 调用客户端。
     */
    private final RestTemplate restTemplate;

    /**
     * 根据关键词搜索地点。
     *
     * @param suggestSearchDTO 搜索条件
     * @return 搜索结果
     */
    @Override
    public PoiListDTO searchTencentMapPlaceByRegion(SuggestSearchDTO suggestSearchDTO) {
        String url = UriComponentsBuilder.fromHttpUrl(apiServer + MapConstants.TENCENT_MAP_API_PLACE_SUGGESTION)
                .queryParam("key", key)
                .queryParam("region", suggestSearchDTO.getId())
                .queryParam("region_fix", 1)
                .queryParam("page_index", suggestSearchDTO.getPageIndex())
                .queryParam("page_size", suggestSearchDTO.getPageSize())
                .queryParam("keyword", suggestSearchDTO.getKeyword())
                .build()
                .encode()
                .toUriString();

        ResponseEntity<PoiListDTO> response = restTemplate.getForEntity(url, PoiListDTO.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("Tencent map place suggestion query failed, response={}", response);
            throw new ServiceException(ResultCode.TENCENT_MAP_QUERY_FAILED);
        }
        return response.getBody();
    }

    /**
     * 根据经纬度获取区域信息。
     *
     * @param locationDTO 经纬度
     * @return 区域信息
     */
    @Override
    public GeoResultDTO getTencentMapDistrictByLonLat(LocationDTO locationDTO) {
        String url = UriComponentsBuilder.fromHttpUrl(apiServer + MapConstants.TENCENT_MAP_GEOCODER)
                .queryParam("key", key)
                .queryParam("location", locationDTO.formatInfo())
                .build()
                .encode()
                .toUriString();

        ResponseEntity<GeoResultDTO> response = restTemplate.getForEntity(url, GeoResultDTO.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("Tencent map geocoder query failed, response={}", response);
            throw new ServiceException(ResultCode.TENCENT_MAP_QUERY_FAILED);
        }
        return response.getBody();
    }
}
