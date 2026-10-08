package com.anyujia.anyujiaadminservice.map.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置。
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 创建 RestTemplate，用于调用第三方地图服务。
     *
     * @param restTemplateBuilder RestTemplate 构建器
     * @return RestTemplate 实例
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder.build();
    }
}
