package com.anyujia.anyujiagateway.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * 网关认证白名单配置。
 *
 * <p>从配置中心读取 {@code security.ignore.whites}，命中的请求路径不做登录态校验。</p>
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "security.ignore")
public class IgnoreWhiteProperties {

    /**
     * 放行路径列表，支持 Ant 风格路径，例如 /admin/user/login、/actuator/**。
     */
    private List<String> whites = new ArrayList<>();
}
