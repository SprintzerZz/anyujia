package com.anyujia.anyujiamstemplateservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class AnyujiaMstemplateServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(AnyujiaMstemplateServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AnyujiaMstemplateServiceApplication.class, args);
        log.info("模板服务启动成功");
    }
}
