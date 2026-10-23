package com.anyujia.anyujialegalservice;

import com.anyujia.anyujialegalservice.config.LegalAiProperties;
import com.anyujia.anyujialegalservice.config.LegalDocumentProperties;
import com.anyujia.anyujialegalservice.config.LegalMemoryProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@MapperScan("com.anyujia.anyujialegalservice.mapper")
@EnableFeignClients(basePackages = {
        "com.anyujia.anyujiachatapi",
        "com.anyujia.anyujiafileapi",
        "com.anyujia.anyujiahouseapi"
})
@EnableConfigurationProperties({LegalAiProperties.class, LegalDocumentProperties.class, LegalMemoryProperties.class})
@SpringBootApplication(scanBasePackages = "com.anyujia")
public class AnyujiaLegalApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnyujiaLegalApplication.class, args);
    }
}
