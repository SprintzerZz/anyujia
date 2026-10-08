package com.anyujia.anyujiafileservice.domain.dto;

import lombok.Data;

/**
 * OSS 表单直传签名 DTO。
 */
@Data
public class SignDTO {

    /**
     * OSS 上传地址。
     */
    private String host;

    /**
     * 上传文件路径前缀。
     */
    private String pathPrefix;

    /**
     * Base64 编码后的 Policy。
     */
    private String policy;

    /**
     * OSS V4 签名。
     */
    private String signature;

    /**
     * OSS V4 Credential。
     */
    private String xOSSCredential;

    /**
     * OSS V4 请求时间。
     */
    private String xOSSDate;
}
