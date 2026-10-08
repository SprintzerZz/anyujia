package com.anyujia.anyujiacouponservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 优惠券服务启动类。
 */
@MapperScan("com.anyujia.anyujiacouponservice.mapper")
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.anyujia")
public class AnyujiaCouponApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnyujiaCouponApplication.class, args);
    }
}
