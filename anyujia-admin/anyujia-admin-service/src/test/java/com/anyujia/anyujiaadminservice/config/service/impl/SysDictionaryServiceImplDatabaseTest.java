package com.anyujia.anyujiaadminservice.config.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryDataAddReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryDataDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryDataEditReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryDataListReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryTypeListReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryTypeWriteReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.vo.DictionaryDataVO;
import com.anyujia.anyujiaadminapi.config.domain.vo.DictionaryTypeVO;
import com.anyujia.anyujiaadminservice.config.service.ISysDictionaryService;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import org.mybatis.spring.annotation.MapperScan;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典服务数据库集成测试�? *
 * <p>连接真实 MySQL，验�?Service、Mapper、MyBatis-Plus 和数据库字段映射是否完整可用�?/p>
 * <p>每个测试方法都开启事务，测试结束自动回滚，避免测试数据污染开发库�?/p>
 */
@Transactional
@SpringBootTest(classes = SysDictionaryServiceImplDatabaseTest.TestApplication.class)
@TestPropertySource(properties = {
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.datasource.url=jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:frameworkjava_dev}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=${MYSQL_USERNAME:anyujiadev}",
        "spring.datasource.password=${MYSQL_PASSWORD:change-me}"
})
class SysDictionaryServiceImplDatabaseTest {

    /**
     * 字典服务�?     */
    @jakarta.annotation.Resource
    private ISysDictionaryService sysDictionaryService;

    /**
     * 测试新增字典类型，并通过列表接口验证数据库中能查到�?     */
    @Test
    void addTypeShouldWriteDatabaseAndRollbackAfterTest() {
        String suffix = uniqueSuffix();
        DictionaryTypeWriteReqDTO reqDTO = typeReq("test_type_" + suffix, "测试类型" + suffix, "测试新增类型");

        Long id = sysDictionaryService.addType(reqDTO);

        DictionaryTypeListReqDTO listReqDTO = new DictionaryTypeListReqDTO();
        listReqDTO.setTypeKey(reqDTO.getTypeKey());
        BasePageVO<DictionaryTypeVO> pageVO = sysDictionaryService.listType(listReqDTO);

        assertThat(id).isPositive();
        assertThat(pageVO.getList()).hasSize(1);
        assertThat(pageVO.getList().get(0).getValue()).isEqualTo(reqDTO.getValue());
    }

    /**
     * 测试编辑字典类型，并验证 type_name �?VO.value 的字段映射�?     */
    @Test
    void editTypeShouldUpdateTypeName() {
        String suffix = uniqueSuffix();
        DictionaryTypeWriteReqDTO reqDTO = typeReq("test_type_" + suffix, "测试类型" + suffix, "原备�?);
        sysDictionaryService.addType(reqDTO);

        reqDTO.setValue("测试类型已修�? + suffix);
        reqDTO.setRemark("新备�?);
        Long id = sysDictionaryService.editType(reqDTO);

        DictionaryTypeListReqDTO listReqDTO = new DictionaryTypeListReqDTO();
        listReqDTO.setTypeKey(reqDTO.getTypeKey());
        BasePageVO<DictionaryTypeVO> pageVO = sysDictionaryService.listType(listReqDTO);

        assertThat(id).isPositive();
        assertThat(pageVO.getList().get(0).getValue()).isEqualTo(reqDTO.getValue());
        assertThat(pageVO.getList().get(0).getRemark()).isEqualTo("新备�?);
    }

    /**
     * 测试新增字典数据，并验证 data_value �?VO.value 的字段映射�?     */
    @Test
    void addDataShouldWriteDatabaseAndMapDataValue() {
        String suffix = uniqueSuffix();
        String typeKey = "test_type_" + suffix;
        sysDictionaryService.addType(typeReq(typeKey, "测试类型" + suffix, null));

        DictionaryDataAddReqDTO dataReqDTO = dataAddReq(typeKey, "online_" + suffix, "已上�?);
        Long id = sysDictionaryService.addData(dataReqDTO);

        DictionaryDataListReqDTO listReqDTO = new DictionaryDataListReqDTO();
        listReqDTO.setTypeKey(typeKey);
        BasePageVO<DictionaryDataVO> pageVO = sysDictionaryService.listData(listReqDTO);

        assertThat(id).isPositive();
        assertThat(pageVO.getList()).hasSize(1);
        assertThat(pageVO.getList().get(0).getValue()).isEqualTo("已上�?);
    }

    /**
     * 测试数据库唯一约束对应的业务校验：同一 typeKey �?dataKey 不能重复�?     */
    @Test
    void addDataShouldRejectDuplicatedDataKeyInSameType() {
        String suffix = uniqueSuffix();
        String typeKey = "test_type_" + suffix;
        sysDictionaryService.addType(typeReq(typeKey, "测试类型" + suffix, null));
        sysDictionaryService.addData(dataAddReq(typeKey, "online", "已上�?));

        assertThatThrownBy(() -> sysDictionaryService.addData(dataAddReq(typeKey, "online", "重复上架")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("当前字典类型下字典数据键已存�?);
    }

    /**
     * 测试编辑字典数据，并验证�?dataKey 查询能拿到修改后的展示值�?     */
    @Test
    void editDataShouldUpdateDictionaryData() {
        String suffix = uniqueSuffix();
        String typeKey = "test_type_" + suffix;
        sysDictionaryService.addType(typeReq(typeKey, "测试类型" + suffix, null));
        Long dataId = sysDictionaryService.addData(dataAddReq(typeKey, "online_" + suffix, "已上�?));

        DictionaryDataEditReqDTO editReqDTO = new DictionaryDataEditReqDTO();
        editReqDTO.setId(dataId);
        editReqDTO.setTypeKey(typeKey);
        editReqDTO.setDataKey("offline_" + suffix);
        editReqDTO.setValue("已下�?);
        editReqDTO.setSort(2);
        editReqDTO.setStatus(1);
        Long editedId = sysDictionaryService.editData(editReqDTO);

        DictionaryDataDTO dto = sysDictionaryService.getDictDataByKey("offline_" + suffix);

        assertThat(editedId).isEqualTo(dataId);
        assertThat(dto.getValue()).isEqualTo("已下�?);
        assertThat(dto.getSort()).isEqualTo(2);
    }

    /**
     * 测试根据单个 typeKey 查询字典数据�?     */
    @Test
    void selectDictDataByTypeShouldReturnEnabledData() {
        String suffix = uniqueSuffix();
        String typeKey = "test_type_" + suffix;
        sysDictionaryService.addType(typeReq(typeKey, "测试类型" + suffix, null));
        sysDictionaryService.addData(dataAddReq(typeKey, "online_" + suffix, "已上�?));
        sysDictionaryService.addData(dataAddReq(typeKey, "offline_" + suffix, "已下�?));

        List<DictionaryDataDTO> result = sysDictionaryService.selectDictDataByType(typeKey);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DictionaryDataDTO::getValue).containsExactly("已上�?, "已下�?);
    }

    /**
     * 测试根据多个 typeKey 查询字典数据，并�?typeKey 分组返回�?     */
    @Test
    void selectDictDataByTypesShouldReturnGroupedData() {
        String suffix = uniqueSuffix();
        String houseStatusType = "test_house_status_" + suffix;
        String rentType = "test_rent_type_" + suffix;
        sysDictionaryService.addType(typeReq(houseStatusType, "测试房源状�? + suffix, null));
        sysDictionaryService.addType(typeReq(rentType, "测试租赁类型" + suffix, null));
        sysDictionaryService.addData(dataAddReq(houseStatusType, "online_" + suffix, "已上�?));
        sysDictionaryService.addData(dataAddReq(rentType, "whole_" + suffix, "整租"));

        Map<String, List<DictionaryDataDTO>> result = sysDictionaryService.selectDictDataByTypes(List.of(houseStatusType, rentType));

        assertThat(result.get(houseStatusType)).extracting(DictionaryDataDTO::getValue).containsExactly("已上�?);
        assertThat(result.get(rentType)).extracting(DictionaryDataDTO::getValue).containsExactly("整租");
    }

    /**
     * 测试根据多个 dataKey 查询字典数据�?     */
    @Test
    void getDictDataByKeysShouldReturnMatchedData() {
        String suffix = uniqueSuffix();
        String typeKey = "test_type_" + suffix;
        String onlineKey = "online_" + suffix;
        String offlineKey = "offline_" + suffix;
        sysDictionaryService.addType(typeReq(typeKey, "测试类型" + suffix, null));
        sysDictionaryService.addData(dataAddReq(typeKey, onlineKey, "已上�?));
        sysDictionaryService.addData(dataAddReq(typeKey, offlineKey, "已下�?));

        List<DictionaryDataDTO> result = sysDictionaryService.getDictDataByKeys(List.of(onlineKey, offlineKey));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DictionaryDataDTO::getDataKey).containsExactly(onlineKey, offlineKey);
    }

    /**
     * 构造字典类型请求�?     *
     * @param typeKey 字典类型�?     * @param value 字典类型名称
     * @param remark 备注
     * @return 字典类型写请�?     */
    private static DictionaryTypeWriteReqDTO typeReq(String typeKey, String value, String remark) {
        DictionaryTypeWriteReqDTO reqDTO = new DictionaryTypeWriteReqDTO();
        reqDTO.setTypeKey(typeKey);
        reqDTO.setValue(value);
        reqDTO.setRemark(remark);
        return reqDTO;
    }

    /**
     * 构造字典数据新增请求�?     *
     * @param typeKey 字典类型�?     * @param dataKey 字典数据�?     * @param value 字典展示�?     * @return 字典数据新增请求
     */
    private static DictionaryDataAddReqDTO dataAddReq(String typeKey, String dataKey, String value) {
        DictionaryDataAddReqDTO reqDTO = new DictionaryDataAddReqDTO();
        reqDTO.setTypeKey(typeKey);
        reqDTO.setDataKey(dataKey);
        reqDTO.setValue(value);
        reqDTO.setSort(1);
        reqDTO.setStatus(1);
        return reqDTO;
    }

    /**
     * 生成短随机后缀，避免和开发库已有数据冲突�?     *
     * @return 随机后缀
     */
    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 字典数据库测试专�?Spring Boot 配置�?     *
     * <p>只扫描字典服务实现和字典 Mapper，避免加载地图、缓存、MQ 等无关模块�?/p>
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
            RedissonAutoConfigurationV2.class
    })
    @ComponentScan("com.anyujia.anyujiaadminservice.config.service.impl")
    @MapperScan("com.anyujia.anyujiaadminservice.config.mapper")
    static class TestApplication {
    }
}
