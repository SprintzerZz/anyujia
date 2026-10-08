package com.anyujia.anyujiafileservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 文件服务启动类。
 */
@SpringBootApplication
public class AnyujiaFileServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(AnyujiaFileServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AnyujiaFileServiceApplication.class, args);
        log.info("文件服务启动成功");
    }
}
