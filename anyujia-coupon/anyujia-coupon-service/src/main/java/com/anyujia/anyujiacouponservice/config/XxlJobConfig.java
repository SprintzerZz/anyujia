package com.anyujia.anyujiacouponservice.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "xxl.job", name = "enabled", havingValue = "true")
public class XxlJobConfig {

    @Bean
    @ConfigurationProperties(prefix = "xxl.job")
    public XxlJobProperties xxlJobProperties() {
        return new XxlJobProperties();
    }

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.getAdmin().getAddresses());
        executor.setAppname(properties.getExecutor().getAppname());
        executor.setIp(properties.getExecutor().getIp());
        executor.setPort(properties.getExecutor().getPort());
        executor.setAccessToken(properties.getAccessToken());
        executor.setLogPath(properties.getExecutor().getLogpath());
        executor.setLogRetentionDays(properties.getExecutor().getLogretentiondays());
        return executor;
    }

    @Getter
    @Setter
    public static class XxlJobProperties {
        private Admin admin = new Admin();
        private Executor executor = new Executor();
        private String accessToken;

        @Getter
        @Setter
        public static class Admin {
            private String addresses;
        }

        @Getter
        @Setter
        public static class Executor {
            private String appname = "anyujia-coupon";
            private String ip;
            private int port = 9999;
            private String logpath = "/tmp/xxl-job";
            private int logretentiondays = 7;
        }
    }
}
