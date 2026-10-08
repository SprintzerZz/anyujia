package com.anyujia.anyujiafileservice.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 配置信息。
 *
 * <p>配置从 Spring Environment 读取，可以来自本地配置文件，也可以来自 Nacos。
 * 当 {@code storage.type=oss} 时该配置类生效，配置前缀为 {@code oss}。</p>
 */
@Slf4j
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "oss")
@ConditionalOnProperty(value = "storage.type", havingValue = "oss")
public class OSSProperties {

    /**
     * 是否使用 OSS 内网 endpoint 上传。
     *
     * <p>true 表示使用 {@link #intEndpoint}，false 表示使用 {@link #endpoint}。</p>
     */
    private Boolean internal = false;

    /**
     * OSS 公网 endpoint。
     */
    private String endpoint;

    /**
     * OSS 内网 endpoint。
     */
    private String intEndpoint;

    /**
     * OSS 地域代码，例如 {@code cn-hangzhou}。
     */
    private String region;

    /**
     * 阿里云 AccessKey ID。
     */
    private String accessKeyId;

    /**
     * 阿里云 AccessKey Secret。
     */
    private String accessKeySecret;

    /**
     * OSS 存储桶名称。
     */
    private String bucketName;

    /**
     * 文件访问路径前缀。
     *
     * <p>该前缀通常拼接在 endpoint 后面，用于区分业务目录，例如 {@code avatar/}、
     * {@code upload/} 等。</p>
     */
    private String pathPrefix;

    /**
     * 获取公网访问基础 URL。
     *
     * @return 公网访问基础 URL
     */
    public String getBaseUrl() {
        return "https://" + bucketName + "." + endpoint + "/";
    }

    /**
     * 获取内网访问基础 URL。
     *
     * @return 内网访问基础 URL
     */
    public String getInternalBaseUrl() {
        return "http://" + bucketName + "." + intEndpoint + "/";
    }
}
