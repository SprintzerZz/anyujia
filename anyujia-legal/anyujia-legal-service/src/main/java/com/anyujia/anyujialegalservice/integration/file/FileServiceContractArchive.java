package com.anyujia.anyujialegalservice.integration.file;

import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.anyujia.anyujiafileapi.domain.vo.FileVO;
import com.anyujia.anyujiafileapi.feign.FileFeignClient;
import com.anyujia.anyujialegalservice.service.ContractArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class FileServiceContractArchive implements ContractArchiveService {
    private final FileFeignClient fileFeignClient;

    @Override
    public String archive(MultipartFile file) {
        ApiResult<FileVO> response = fileFeignClient.upload(file);
        if (response == null || response.getCode() != ResultCode.SUCCESS.getCode()
                || response.getData() == null || !StringUtils.hasText(response.getData().getKey())) {
            throw new ServiceException("合同原件归档到 OSS 失败", ResultCode.ERROR.getCode());
        }
        return response.getData().getKey();
    }
}
