package com.anyujia.anyujiachatservice.service.house;

import com.anyujia.anyujiahouseapi.domain.vo.HousePublishConsultVO;
import com.anyujia.anyujiahouseapi.feign.HousePublishFeignClient;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeignChatHousePublishProvider implements ChatHousePublishProvider {
    private final HousePublishFeignClient housePublishFeignClient;

    @Override
    public HousePublishConsultVO getRequired(Long housePublishId) {
        ApiResult<HousePublishConsultVO> response = housePublishFeignClient.getConsultable(housePublishId);
        if (response == null || response.getCode() != ResultCode.SUCCESS.getCode() || response.getData() == null) {
            throw new ServiceException("Failed to validate house publication");
        }
        return response.getData();
    }
}
