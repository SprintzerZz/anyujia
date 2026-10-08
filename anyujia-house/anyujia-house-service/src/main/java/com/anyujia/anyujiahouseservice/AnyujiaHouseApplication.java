package com.anyujia.anyujiahouseservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.anyujia.anyujiahouseservice.mapper")
@SpringBootApplication(scanBasePackages = "com.anyujia")
public class AnyujiaHouseApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnyujiaHouseApplication.class, args);
    }
}
