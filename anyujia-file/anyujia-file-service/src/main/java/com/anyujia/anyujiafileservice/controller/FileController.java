package com.anyujia.anyujiafileservice.controller;

import com.anyujia.anyujiacommoncore.utils.BeanCopyUtil;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiafileapi.domain.vo.FileVO;
import com.anyujia.anyujiafileapi.feign.FileFeignClient;
import com.anyujia.anyujiafileservice.domain.dto.FileDTO;
import com.anyujia.anyujiafileservice.domain.dto.SignDTO;
import com.anyujia.anyujiafileservice.domain.vo.SignVO;
import com.anyujia.anyujiafileservice.service.IOSSFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件接口。
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(value = "storage.type", havingValue = "oss")
public class FileController implements FileFeignClient {

    private final IOSSFileService IFileService;

    /**
     * 上传文件。
     *
     * <p>网关按服务前缀转发时，外部通常访问 {@code /file/upload}，
     * 文件服务内部接口保持为 {@code /upload}。</p>
     *
     * @param file 上传文件，表单字段名为 file
     * @return 上传后的文件信息
     */
    @Override
    public ApiResult<FileVO> upload(@RequestPart("file") MultipartFile file) {
        FileDTO fileDTO = IFileService.upload(file);
        FileVO fileVO = new FileVO();
        BeanCopyUtil.copyProperties(fileDTO, fileVO);
        return ApiResult.success(fileVO);
    }

    /**
     * 获取 OSS 表单直传签名。
     *
     * @return OSS 表单直传签名信息
     */
    @GetMapping("/getSign")
    public ApiResult<SignVO> getSign() {
        SignDTO signDTO = IFileService.getSign();
        SignVO signVO = new SignVO();
        BeanCopyUtil.copyProperties(signDTO, signVO);
        return ApiResult.success(signVO);
    }
}
