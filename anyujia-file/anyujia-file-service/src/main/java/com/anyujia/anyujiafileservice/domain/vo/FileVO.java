package com.anyujia.anyujiafileservice.domain.vo;

import lombok.Data;

/**
 * 文件上传响应 VO。
 */
@Data
public class FileVO {

    /**
     * 文件访问地址。
     */
    private String url;

    /**
     * OSS 文件 key，等同于对象名称。
     */
    private String key;

    /**
     * 文件名，不包含 OSS 路径前缀。
     */
    private String name;

    /**
     * OSS 存储桶名称。
     */
    private String bucketName;

    /**
     * OSS 对象名称，包含业务前缀和文件名。
     */
    private String objectName;

    /**
     * 生成后的文件名。
     */
    private String fileName;

    /**
     * 上传时的原始文件名。
     */
    private String originalFilename;

    /**
     * 文件内容类型。
     */
    private String contentType;

    /**
     * 文件大小，单位字节。
     */
    private Long size;
}
