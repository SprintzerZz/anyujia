package com.anyujia.anyujiaadminservice.config.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryDataAddReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryDataDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryDataEditReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryDataListReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryTypeListReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryTypeWriteReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.vo.DictionaryDataVO;
import com.anyujia.anyujiaadminapi.config.domain.vo.DictionaryTypeVO;
import com.anyujia.anyujiaadminservice.config.domain.entity.SysDictionaryData;
import com.anyujia.anyujiaadminservice.config.domain.entity.SysDictionaryType;
import com.anyujia.anyujiaadminservice.config.mapper.SysDictionaryDataMapper;
import com.anyujia.anyujiaadminservice.config.mapper.SysDictionaryTypeMapper;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

/**
 * 字典服务单元测试。
 *
 * <p>直接 mock Mapper，重点验证业务判断、默认值处理、分页封装和 DTO/VO 转换，不依赖真实数据库和 Nacos。</p>
 */
class SysDictionaryServiceImplTest {

    /**
     * 新增字典类型时，应校验唯一性、写入实体并返回主键。
     */
    @Test
    void addTypeShouldInsertDictionaryTypeAndReturnId() {
        TestFixture fixture = createFixture();
        DictionaryTypeWriteReqDTO reqDTO = typeReq("house_status", "房源状态", "房源状态配置");

        when(fixture.typeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            SysDictionaryType entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(fixture.typeMapper).insert(any(SysDictionaryType.class));

        Long id = fixture.service.addType(reqDTO);

        assertThat(id).isEqualTo(1L);
        verify(fixture.typeMapper).insert(any(SysDictionaryType.class));
    }

    /**
     * 新增字典类型时，如果 typeKey 或 typeName 已存在，应直接抛出业务异常。
     */
    @Test
    void addTypeShouldThrowWhenTypeAlreadyExists() {
        TestFixture fixture = createFixture();
        when(fixture.typeMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> fixture.service.addType(typeReq("house_status", "房源状态", null)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("字典类型键或者名称已存在");
    }

    /**
     * 字典类型分页查询应返回分页信息，并把实体 typeName 转换成 VO value。
     */
    @Test
    void listTypeShouldReturnPageVO() {
        TestFixture fixture = createFixture();
        DictionaryTypeListReqDTO reqDTO = new DictionaryTypeListReqDTO();
        reqDTO.setTypeKey("house_status");
        reqDTO.setPageIndex(1);
        reqDTO.setPageSize(10);

        when(fixture.typeMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<SysDictionaryType> page = invocation.getArgument(0);
            page.setTotal(1L);
            page.setRecords(List.of(typeEntity(1L, "house_status", "房源状态")));
            return page;
        });

        BasePageVO<DictionaryTypeVO> result = fixture.service.listType(reqDTO);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getValue()).isEqualTo("房源状态");
    }

    /**
     * 编辑字典类型时，应按 typeKey 找到原记录，再更新 typeName 和 remark。
     */
    @Test
    void editTypeShouldUpdateDictionaryType() {
        TestFixture fixture = createFixture();
        SysDictionaryType oldEntity = typeEntity(1L, "house_status", "房源状态");
        DictionaryTypeWriteReqDTO reqDTO = typeReq("house_status", "房源上下架状态", "更新备注");

        when(fixture.typeMapper.selectOne(any(Wrapper.class))).thenReturn(oldEntity);
        when(fixture.typeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(fixture.typeMapper.updateById(any(SysDictionaryType.class))).thenReturn(1);

        Long id = fixture.service.editType(reqDTO);

        assertThat(id).isEqualTo(1L);
        assertThat(oldEntity.getTypeName()).isEqualTo("房源上下架状态");
        assertThat(oldEntity.getRemark()).isEqualTo("更新备注");
    }

    /**
     * 新增字典数据时，应校验类型存在、同类型 dataKey 不重复，并补齐默认 sort/status。
     */
    @Test
    void addDataShouldInsertDictionaryDataAndReturnId() {
        TestFixture fixture = createFixture();
        DictionaryDataAddReqDTO reqDTO = dataAddReq("house_status", "online", "已上架");

        when(fixture.typeMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(fixture.dataMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            SysDictionaryData entity = invocation.getArgument(0);
            entity.setId(10L);
            return 1;
        }).when(fixture.dataMapper).insert(any(SysDictionaryData.class));

        Long id = fixture.service.addData(reqDTO);

        assertThat(id).isEqualTo(10L);
        verify(fixture.dataMapper).insert(any(SysDictionaryData.class));
    }

    /**
     * 新增字典数据时，如果同一 typeKey 下 dataKey 已存在，应抛出业务异常。
     */
    @Test
    void addDataShouldThrowWhenDataKeyExistsInSameType() {
        TestFixture fixture = createFixture();
        when(fixture.typeMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(fixture.dataMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> fixture.service.addData(dataAddReq("house_status", "online", "已上架")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("当前字典类型下字典数据键已存在");
    }

    /**
     * 字典数据分页查询应返回分页结果，并把实体 dataValue 映射成 VO value。
     */
    @Test
    void listDataShouldReturnPageVO() {
        TestFixture fixture = createFixture();
        DictionaryDataListReqDTO reqDTO = new DictionaryDataListReqDTO();
        reqDTO.setTypeKey("house_status");
        reqDTO.setPageIndex(1);
        reqDTO.setPageSize(1);

        when(fixture.dataMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<SysDictionaryData> page = invocation.getArgument(0);
            page.setTotal(2L);
            page.setRecords(List.of(dataEntity(1L, "house_status", "online", "已上架")));
            return page;
        });

        BasePageVO<DictionaryDataVO> result = fixture.service.listData(reqDTO);

        assertThat(result.getPageSize()).isEqualTo(1);
        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getList()).extracting(DictionaryDataVO::getValue).containsExactly("已上架");
    }

    /**
     * 编辑字典数据时，应按 id 查询原记录，更新业务键、展示值、排序和状态。
     */
    @Test
    void editDataShouldUpdateDictionaryData() {
        TestFixture fixture = createFixture();
        SysDictionaryData oldEntity = dataEntity(1L, "house_status", "online", "已上架");
        DictionaryDataEditReqDTO reqDTO = new DictionaryDataEditReqDTO();
        reqDTO.setId(1L);
        reqDTO.setTypeKey("house_status");
        reqDTO.setDataKey("offline");
        reqDTO.setValue("已下架");
        reqDTO.setSort(2);
        reqDTO.setStatus(1);

        when(fixture.typeMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(fixture.dataMapper.selectById(1L)).thenReturn(oldEntity);
        when(fixture.dataMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(fixture.dataMapper.updateById(any(SysDictionaryData.class))).thenReturn(1);

        Long id = fixture.service.editData(reqDTO);

        assertThat(id).isEqualTo(1L);
        assertThat(oldEntity.getDataKey()).isEqualTo("offline");
        assertThat(oldEntity.getValue()).isEqualTo("已下架");
        assertThat(oldEntity.getSort()).isEqualTo(2);
    }

    /**
     * 调试接口时如果误传了字典类型 ID，应按 typeKey + dataKey 定位真实字典数据后再更新。
     */
    @Test
    void editDataShouldFallbackToTypeKeyAndDataKeyWhenIdIsWrong() {
        TestFixture fixture = createFixture();
        SysDictionaryData oldEntity = dataEntity(2L, "house_status", "online", "已上架");
        DictionaryDataEditReqDTO reqDTO = new DictionaryDataEditReqDTO();
        reqDTO.setId(31L);
        reqDTO.setTypeKey("house_status");
        reqDTO.setDataKey("online");
        reqDTO.setValue("已上架");
        reqDTO.setSort(1);
        reqDTO.setStatus(1);
        reqDTO.setRemark("展示中的房源");

        when(fixture.typeMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(fixture.dataMapper.selectById(31L)).thenReturn(null);
        when(fixture.dataMapper.selectOne(any(Wrapper.class))).thenReturn(oldEntity);
        when(fixture.dataMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(fixture.dataMapper.updateById(any(SysDictionaryData.class))).thenReturn(1);

        Long id = fixture.service.editData(reqDTO);

        assertThat(id).isEqualTo(2L);
        assertThat(oldEntity.getDataKey()).isEqualTo("online");
        assertThat(oldEntity.getSort()).isEqualTo(1);
        assertThat(oldEntity.getRemark()).isEqualTo("展示中的房源");
    }

    /**
     * 根据 typeKey 查询字典数据时，只返回启用数据列表。
     */
    @Test
    void selectDictDataByTypeShouldReturnDTOList() {
        TestFixture fixture = createFixture();
        when(fixture.dataMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                dataEntity(1L, "house_status", "online", "已上架"),
                dataEntity(2L, "house_status", "offline", "已下架")
        ));

        List<DictionaryDataDTO> result = fixture.service.selectDictDataByType("house_status");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DictionaryDataDTO::getDataKey).containsExactly("online", "offline");
    }

    /**
     * 根据多个 typeKey 查询字典数据时，应按 typeKey 分组返回。
     */
    @Test
    void selectDictDataByTypesShouldGroupByTypeKey() {
        TestFixture fixture = createFixture();
        when(fixture.dataMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                dataEntity(1L, "house_status", "online", "已上架"),
                dataEntity(2L, "rent_type", "whole", "整租")
        ));

        Map<String, List<DictionaryDataDTO>> result = fixture.service.selectDictDataByTypes(List.of("house_status", "rent_type"));

        assertThat(result.get("house_status")).extracting(DictionaryDataDTO::getValue).containsExactly("已上架");
        assertThat(result.get("rent_type")).extracting(DictionaryDataDTO::getValue).containsExactly("整租");
    }

    /**
     * 根据 dataKey 查询字典数据时，应返回单个 DTO。
     */
    @Test
    void getDictDataByKeyShouldReturnDTO() {
        TestFixture fixture = createFixture();
        when(fixture.dataMapper.selectOne(any(Wrapper.class)))
                .thenReturn(dataEntity(1L, "house_status", "online", "已上架"));

        DictionaryDataDTO result = fixture.service.getDictDataByKey("online");

        assertThat(result.getValue()).isEqualTo("已上架");
    }

    /**
     * 根据多个 dataKey 查询字典数据时，应返回 DTO 列表。
     */
    @Test
    void getDictDataByKeysShouldReturnDTOList() {
        TestFixture fixture = createFixture();
        when(fixture.dataMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                dataEntity(1L, "house_status", "online", "已上架"),
                dataEntity(2L, "house_status", "offline", "已下架")
        ));

        List<DictionaryDataDTO> result = fixture.service.getDictDataByKeys(List.of("online", "offline"));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DictionaryDataDTO::getValue).containsExactly("已上架", "已下架");
    }

    /**
     * 创建测试夹具。
     *
     * @return 测试夹具
     */
    private static TestFixture createFixture() {
        SysDictionaryTypeMapper typeMapper = mock(SysDictionaryTypeMapper.class);
        SysDictionaryDataMapper dataMapper = mock(SysDictionaryDataMapper.class);
        SysDictionaryServiceImpl service = new SysDictionaryServiceImpl(typeMapper, dataMapper);
        return new TestFixture(typeMapper, dataMapper, service);
    }

    /**
     * 构造字典类型请求。
     *
     * @param typeKey 字典类型键
     * @param value 字典类型名称
     * @param remark 备注
     * @return 字典类型写请求
     */
    private static DictionaryTypeWriteReqDTO typeReq(String typeKey, String value, String remark) {
        DictionaryTypeWriteReqDTO reqDTO = new DictionaryTypeWriteReqDTO();
        reqDTO.setTypeKey(typeKey);
        reqDTO.setValue(value);
        reqDTO.setRemark(remark);
        return reqDTO;
    }

    /**
     * 构造字典数据新增请求。
     *
     * @param typeKey 字典类型键
     * @param dataKey 字典数据键
     * @param value 字典展示值
     * @return 字典数据新增请求
     */
    private static DictionaryDataAddReqDTO dataAddReq(String typeKey, String dataKey, String value) {
        DictionaryDataAddReqDTO reqDTO = new DictionaryDataAddReqDTO();
        reqDTO.setTypeKey(typeKey);
        reqDTO.setDataKey(dataKey);
        reqDTO.setValue(value);
        return reqDTO;
    }

    /**
     * 构造字典类型实体。
     *
     * @param id 主键 ID
     * @param typeKey 字典类型键
     * @param typeName 字典类型名称
     * @return 字典类型实体
     */
    private static SysDictionaryType typeEntity(Long id, String typeKey, String typeName) {
        SysDictionaryType entity = new SysDictionaryType();
        entity.setId(id);
        entity.setTypeKey(typeKey);
        entity.setTypeName(typeName);
        entity.setStatus(1);
        return entity;
    }

    /**
     * 构造字典数据实体。
     *
     * @param id 主键 ID
     * @param typeKey 字典类型键
     * @param dataKey 字典数据键
     * @param value 字典展示值
     * @return 字典数据实体
     */
    private static SysDictionaryData dataEntity(Long id, String typeKey, String dataKey, String value) {
        SysDictionaryData entity = new SysDictionaryData();
        entity.setId(id);
        entity.setTypeKey(typeKey);
        entity.setDataKey(dataKey);
        entity.setValue(value);
        entity.setSort(1);
        entity.setStatus(1);
        return entity;
    }

    /**
     * 字典服务测试夹具。
     *
     * @param typeMapper 字典类型 Mapper
     * @param dataMapper 字典数据 Mapper
     * @param service 被测试的字典服务
     */
    private record TestFixture(
            SysDictionaryTypeMapper typeMapper,
            SysDictionaryDataMapper dataMapper,
            SysDictionaryServiceImpl service) {
    }
}
