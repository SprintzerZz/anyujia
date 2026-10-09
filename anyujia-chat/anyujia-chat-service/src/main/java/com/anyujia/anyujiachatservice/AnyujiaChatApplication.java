package com.anyujia.anyujiachatservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.anyujia.anyujiachatservice.mapper")
@EnableFeignClients(basePackages = {"com.anyujia.anyujiaadminapi", "com.anyujia.anyujiahouseapi", "com.anyujia.anyujialegalapi"})
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.anyujia")
@ConfigurationPropertiesScan(basePackages = "com.anyujia.anyujiachatservice")
public class AnyujiaChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnyujiaChatApplication.class, args);
    }
}
