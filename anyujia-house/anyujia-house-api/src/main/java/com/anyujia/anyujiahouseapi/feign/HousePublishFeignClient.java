package com.anyujia.anyujiahouseapi.feign;

import com.anyujia.anyujiahouseapi.domain.vo.HousePublishConsultVO;
import com.anyujia.anyujiahouseapi.domain.vo.HousePublishVO;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(contextId = "housePublishFeignClient", value = "anyujia-house",
        url = "${HOUSE_SERVICE_URL:}", path = "/internal/house-publish")
public interface HousePublishFeignClient {
    @GetMapping("/{housePublishId}/consultable")
    ApiResult<HousePublishConsultVO> getConsultable(@PathVariable("housePublishId") Long housePublishId);

    @GetMapping("/{housePublishId}/snapshot")
    ApiResult<HousePublishVO> getSnapshot(@PathVariable("housePublishId") Long housePublishId);
}
