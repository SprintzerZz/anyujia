package com.anyujia.anyujiaadminservice.config.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.mybatis.spring.annotation.MapperScan;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典接口数据库集成测试�? *
 * <p>使用 MockMvc �?URL 调用真实 Controller，Controller 再调用真�?Service �?Mapper 操作 MySQL�?/p>
 * <p>测试方法开启事务，MockMvc 在同一测试线程中执行，请求产生的数据库变更会在测试结束后自动回滚�?/p>
 */
@Transactional
@AutoConfigureMockMvc
@SpringBootTest(classes = DictionaryControllerDatabaseTest.TestApplication.class)
@TestPropertySource(properties = {
        "server.servlet.context-path=/admin",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.datasource.url=jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:frameworkjava_dev}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=${MYSQL_USERNAME:anyujiadev}",
        "spring.datasource.password=${MYSQL_PASSWORD:change-me}"
})
class DictionaryControllerDatabaseTest {

    /**
     * MockMvc，用于在测试中按 URL 调用接口�?     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * 测试通过 URL 新增字典类型，再通过 URL 查询验证 MySQL 中能读到数据�?     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void addTypeUrlShouldWriteMysqlAndRollbackAfterTest() throws Exception {
        String suffix = uniqueSuffix();
        String typeKey = "test_type_" + suffix;
        String value = "测试类型" + suffix;

        mockMvc.perform(post("/admin/dictionary_type/add")
                        .contextPath("/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "typeKey": "%s",
                                  "value": "%s",
                                  "remark": "URL新增字典类型"
                                }
                                """.formatted(typeKey, value)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200000))
                .andExpect(jsonPath("$.data").isNumber());

        mockMvc.perform(get("/admin/dictionary_type/list")
                        .contextPath("/admin")
                        .param("typeKey", typeKey)
                        .param("pageIndex", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200000))
                .andExpect(jsonPath("$.data.list", hasSize(1)))
                .andExpect(jsonPath("$.data.list[0].typeKey").value(typeKey))
                .andExpect(jsonPath("$.data.list[0].value").value(value));
    }

    /**
     * 测试通过 URL 新增字典数据，再通过 URL 查询验证 data_value 字段映射到响�?value�?     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void addDataUrlShouldWriteMysqlAndReturnDataValue() throws Exception {
        String suffix = uniqueSuffix();
        String typeKey = "test_type_" + suffix;
        String dataKey = "online_" + suffix;

        addTypeByUrl(typeKey, "测试类型" + suffix);

        mockMvc.perform(post("/admin/dictionary_data/add")
                        .contextPath("/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "typeKey": "%s",
                                  "dataKey": "%s",
                                  "value": "已上�?,
                                  "sort": 1,
                                  "status": 1,
                                  "remark": "URL新增字典数据"
                                }
                                """.formatted(typeKey, dataKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200000))
                .andExpect(jsonPath("$.data").isNumber());

        mockMvc.perform(get("/admin/dictionary_data/list")
                        .contextPath("/admin")
                        .param("typeKey", typeKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200000))
                .andExpect(jsonPath("$.data.list", hasSize(1)))
                .andExpect(jsonPath("$.data.list[0].dataKey").value(dataKey))
                .andExpect(jsonPath("$.data.list[0].value").value("已上�?));
    }

    /**
     * 测试通过 URL 编辑字典数据，并通过 dataKey 查询验证修改结果�?     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void editDataUrlShouldUpdateMysqlData() throws Exception {
        String suffix = uniqueSuffix();
        String typeKey = "test_type_" + suffix;
        String oldDataKey = "online_" + suffix;
        String newDataKey = "offline_" + suffix;

        addTypeByUrl(typeKey, "测试类型" + suffix);
        Long dataId = addDataByUrl(typeKey, oldDataKey, "已上�?);

        mockMvc.perform(post("/admin/dictionary_data/edit")
                        .contextPath("/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": %d,
                                  "typeKey": "%s",
                                  "dataKey": "%s",
                                  "value": "已下�?,
                                  "sort": 2,
                                  "status": 1,
                                  "remark": "URL编辑字典数据"
                                }
                                """.formatted(dataId, typeKey, newDataKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200000))
                .andExpect(jsonPath("$.data").value(dataId));

        mockMvc.perform(get("/admin/dictionary_data/key")
                        .contextPath("/admin")
                        .param("dataKey", newDataKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200000))
                .andExpect(jsonPath("$.data.dataKey").value(newDataKey))
                .andExpect(jsonPath("$.data.value").value("已下�?))
                .andExpect(jsonPath("$.data.sort").value(2));
    }

    /**
     * 复现 Apifox 调试场景：请�?id 不是字典数据 ID 时，仍可�?typeKey + dataKey 定位并更新�?     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void editDataUrlShouldFallbackToBusinessKeyWhenIdIsWrong() throws Exception {
        String suffix = uniqueSuffix();
        String typeKey = "house_status_" + suffix;
        String dataKey = "online_" + suffix;

        addTypeByUrl(typeKey, "房源状�? + suffix);
        Long dataId = addDataByUrl(typeKey, dataKey, "待上�?);

        mockMvc.perform(post("/admin/dictionary_data/edit")
                        .contextPath("/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 31,
                                  "typeKey": "%s",
                                  "dataKey": "%s",
                                  "value": "已上�?,
                                  "sort": 1,
                                  "status": 1,
                                  "remark": "展示中的房源"
                                }
                                """.formatted(typeKey, dataKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200000))
                .andExpect(jsonPath("$.data").value(dataId));

        mockMvc.perform(get("/admin/dictionary_data/list")
                        .contextPath("/admin")
                        .param("typeKey", typeKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200000))
                .andExpect(jsonPath("$.data.list", hasSize(1)))
                .andExpect(jsonPath("$.data.list[0].dataKey").value(dataKey))
                .andExpect(jsonPath("$.data.list[0].value").value("已上�?))
                .andExpect(jsonPath("$.data.list[0].sort").value(1))
                .andExpect(jsonPath("$.data.list[0].remark").value("展示中的房源"));
    }

    /**
     * 通过 URL 新增字典类型�?     *
     * @param typeKey 字典类型�?     * @param value 字典类型名称
     * @throws Exception MockMvc 调用异常
     */
    private void addTypeByUrl(String typeKey, String value) throws Exception {
        mockMvc.perform(post("/admin/dictionary_type/add")
                        .contextPath("/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "typeKey": "%s",
                                  "value": "%s"
                                }
                                """.formatted(typeKey, value)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200000));
    }

    /**
     * 通过 URL 新增字典数据�?     *
     * @param typeKey 字典类型�?     * @param dataKey 字典数据�?     * @param value 字典展示�?     * @return 字典数据主键 ID
     * @throws Exception MockMvc 调用异常
     */
    private Long addDataByUrl(String typeKey, String dataKey, String value) throws Exception {
        String response = mockMvc.perform(post("/admin/dictionary_data/add")
                        .contextPath("/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "typeKey": "%s",
                                  "dataKey": "%s",
                                  "value": "%s",
                                  "sort": 1,
                                  "status": 1
                                }
                                """.formatted(typeKey, dataKey, value)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200000))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return Long.valueOf(response.replaceAll(".*\"data\":(\\d+).*", "$1"));
    }

    /**
     * 生成短随机后缀，避免和开发库已有数据冲突�?     *
     * @return 随机后缀
     */
    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 字典 URL 数据库测试专�?Spring Boot 配置�?     *
     * <p>只扫描字�?Controller、Service �?Mapper，避免加载地图、缓存、MQ 等无关模块�?/p>
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
            RedissonAutoConfigurationV2.class
    })
    @ComponentScan({
            "com.anyujia.anyujiaadminservice.config.controller",
            "com.anyujia.anyujiaadminservice.config.service.impl"
    })
    @MapperScan("com.anyujia.anyujiaadminservice.config.mapper")
    static class TestApplication {
    }
}
