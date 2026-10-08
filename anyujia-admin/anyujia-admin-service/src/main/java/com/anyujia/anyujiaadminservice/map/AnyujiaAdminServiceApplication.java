package com.anyujia.anyujiaadminservice.map;

import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 基础管理服务启动类。
 */
@MapperScan({
        "com.anyujia.anyujiaadminservice.map.mapper",
        "com.anyujia.anyujiaadminservice.config.mapper",
        "com.anyujia.anyujiaadminservice.user.mapper"
})
@SpringBootApplication(scanBasePackages = "com.anyujia")
public class AnyujiaAdminServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(AnyujiaAdminServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AnyujiaAdminServiceApplication.class, args);
        log.info("基础管理服务启动成功");
    }
}
