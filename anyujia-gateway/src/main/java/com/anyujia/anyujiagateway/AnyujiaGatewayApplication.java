package com.anyujia.anyujiagateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AnyujiaGatewayApplication {

    private static final Logger log = LoggerFactory.getLogger(AnyujiaGatewayApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AnyujiaGatewayApplication.class, args);
        log.info("网关启动成功");
    }
}
