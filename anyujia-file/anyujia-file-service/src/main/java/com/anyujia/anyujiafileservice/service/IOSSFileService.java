package com.anyujia.anyujiafileservice.service;

import com.anyujia.anyujiafileservice.domain.dto.FileDTO;
import com.anyujia.anyujiafileservice.domain.dto.SignDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务。
 */
public interface IOSSFileService {

    /**
     * 上传文件。
     *
     * @param file 上传文件
     * @return 文件上传结果
     */
    FileDTO upload(MultipartFile file);

    /**
     * 获取 OSS 表单直传签名。
     *
     * @return OSS 表单直传签名信息
     */
    SignDTO getSign();
}
