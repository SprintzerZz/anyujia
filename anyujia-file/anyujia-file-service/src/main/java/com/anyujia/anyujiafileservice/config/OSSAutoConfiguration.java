package com.anyujia.anyujiafileservice.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 自动配置。
 *
 * <p>当配置项 {@code storage.type=oss} 时启用该配置，创建并注册阿里云 OSS 客户端。
 * 业务代码可以直接注入 {@link OSSClient} 进行文件上传、下载、删除等操作。</p>
 */
@Configuration
//当storage.type=oss时启用该配置
@ConditionalOnProperty(value = "storage.type", havingValue = "oss")
public class OSSAutoConfiguration {

    /**
     * OSS 客户端。
     *
     * <p>保存当前配置类创建的客户端实例，用于应用关闭时主动释放底层 HTTP 连接资源。</p>
     */
    private OSSClient ossClient;

    /**
     * 初始化 OSS 客户端。
     *
     * <p>客户端使用 AccessKeyId 和 AccessKeySecret 进行认证，并启用 V4 签名。
     * 当 {@code oss.internal=true} 时使用内网 endpoint，否则使用公网 endpoint。</p>
     *
     * @param prop OSS 配置属性
     * @return OSS 客户端
     * @throws ClientException 客户端初始化异常
     */
    @Bean
    public OSSClient ossClient(OSSProperties prop) throws ClientException {
        DefaultCredentialProvider credentialsProvider =
                CredentialsProviderFactory.newDefaultCredentialProvider(
                        prop.getAccessKeyId(), prop.getAccessKeySecret());

        ClientBuilderConfiguration clientConfiguration = new ClientBuilderConfiguration();
        clientConfiguration.setSignatureVersion(SignVersion.V4);
        //内网和外网切换
        String endpoint = Boolean.TRUE.equals(prop.getInternal())
                ? prop.getIntEndpoint()
                : prop.getEndpoint();

        ossClient = (OSSClient) OSSClientBuilder.create()
                .endpoint(endpoint)
                .region(prop.getRegion())
                .credentialsProvider(credentialsProvider)
                .clientConfiguration(clientConfiguration)
                .build();

        return ossClient;
    }

    /**
     * 关闭 OSS 客户端。
     *
     * <p>应用停止时释放 OSS 客户端持有的连接池和线程资源。</p>
     */
    @PreDestroy
    public void closeOSSClient() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }
}
