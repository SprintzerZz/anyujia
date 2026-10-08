package com.anyujia.anyujiafileapi.feign;

import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiafileapi.domain.vo.FileVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(contextId = "fileFeignClient", value = "anyujia-file",
        url = "${FILE_SERVICE_URL:}")
public interface FileFeignClient {
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResult<FileVO> upload(@RequestPart("file") MultipartFile file);
}
