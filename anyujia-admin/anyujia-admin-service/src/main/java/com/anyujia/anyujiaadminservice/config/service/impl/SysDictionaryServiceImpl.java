package com.anyujia.anyujiaadminservice.config.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
import com.anyujia.anyujiaadminservice.config.service.ISysDictionaryService;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 字典服务实现类。
 *
 * <p>负责字典类型和字典数据的新增、编辑、分页查询，以及业务侧按 key 读取字典数据。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictionaryServiceImpl implements ISysDictionaryService {

    /**
     * 启用状态。
     */
    private static final int STATUS_ENABLED = 1;

    /**
     * 默认排序值。
     */
    private static final int DEFAULT_SORT = 0;

    /**
     * 最大分页大小，避免一次查询过多数据。
     */
    private static final long MAX_PAGE_SIZE = 500L;

    /**
     * 字典类型 Mapper。
     */
    private final SysDictionaryTypeMapper sysDictionaryTypeMapper;

    /**
     * 字典数据 Mapper。
     */
    private final SysDictionaryDataMapper sysDictionaryDataMapper;

    /**
     * 新增字典类型。
     *
     * @param dictionaryTypeWriteReqDTO 字典类型写操作请求
     * @return 字典类型主键 ID
     */
    @Override
    public Long addType(DictionaryTypeWriteReqDTO dictionaryTypeWriteReqDTO) {
        // 1. 校验字典类型键和名称不能重复。
        checkTypeNotExists(dictionaryTypeWriteReqDTO.getTypeKey(), dictionaryTypeWriteReqDTO.getValue(), null);

        // 2. 将接口请求 DTO 转换成数据库实体。
        SysDictionaryType sysDictionaryType = new SysDictionaryType();
        sysDictionaryType.setTypeKey(dictionaryTypeWriteReqDTO.getTypeKey());
        sysDictionaryType.setTypeName(dictionaryTypeWriteReqDTO.getValue());
        sysDictionaryType.setRemark(dictionaryTypeWriteReqDTO.getRemark());
        sysDictionaryType.setStatus(STATUS_ENABLED);

        // 3. 写入数据库，MyBatis-Plus 会回填自增主键。
        sysDictionaryTypeMapper.insert(sysDictionaryType);
        return sysDictionaryType.getId();
    }

    /**
     * 分页查询字典类型列表。
     *
     * @param dictionaryTypeListReqDTO 字典类型分页查询请求
     * @return 字典类型分页结果
     */
    @Override
    public BasePageVO<DictionaryTypeVO> listType(DictionaryTypeListReqDTO dictionaryTypeListReqDTO) {
        // 1. 组装动态查询条件：传了条件才拼接，未传则忽略。
        LambdaQueryWrapper<SysDictionaryType> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StringUtils.hasText(dictionaryTypeListReqDTO.getTypeKey()),
                SysDictionaryType::getTypeKey, dictionaryTypeListReqDTO.getTypeKey());
        queryWrapper.likeRight(StringUtils.hasText(dictionaryTypeListReqDTO.getValue()),
                SysDictionaryType::getTypeName, dictionaryTypeListReqDTO.getValue());
        queryWrapper.eq(dictionaryTypeListReqDTO.getStatus() != null,
                SysDictionaryType::getStatus, dictionaryTypeListReqDTO.getStatus());
        queryWrapper.orderByDesc(SysDictionaryType::getId);

        // 2. 执行 MyBatis-Plus 分页查询。
        Page<SysDictionaryType> page = sysDictionaryTypeMapper.selectPage(
                new Page<>(getPageIndex(dictionaryTypeListReqDTO.getPageIndex()), getPageSize(dictionaryTypeListReqDTO.getPageSize())),
                queryWrapper
        );

        // 3. 将数据库实体转换成对外返回的 VO。
        List<DictionaryTypeVO> list = new ArrayList<>();
        for (SysDictionaryType sysDictionaryType : page.getRecords()) {
            list.add(toDictionaryTypeVO(sysDictionaryType));
        }

        // 4. 统一封装分页响应。
        return toBasePageVO(dictionaryTypeListReqDTO.getPageIndex(), dictionaryTypeListReqDTO.getPageSize(), page.getTotal(), list);
    }

    /**
     * 编辑字典类型。
     *
     * <p>当前请求 DTO 未提供主键 ID，因此按字典类型键 {@code typeKey} 定位要编辑的字典类型。</p>
     *
     * @param dictionaryTypeWriteReqDTO 字典类型写操作请求
     * @return 字典类型主键 ID
     */
    @Override
    public Long editType(DictionaryTypeWriteReqDTO dictionaryTypeWriteReqDTO) {
        // 1. 按 typeKey 查询原字典类型。
        SysDictionaryType sysDictionaryType = sysDictionaryTypeMapper.selectOne(
                new LambdaQueryWrapper<SysDictionaryType>()
                        .eq(SysDictionaryType::getTypeKey, dictionaryTypeWriteReqDTO.getTypeKey())
        );
        if (sysDictionaryType == null) {
            throw new ServiceException("字典类型不存在");
        }

        // 2. 排除当前记录后，校验新的类型名称是否和其他记录冲突。
        checkTypeNotExists(dictionaryTypeWriteReqDTO.getTypeKey(), dictionaryTypeWriteReqDTO.getValue(), sysDictionaryType.getId());

        // 3. 更新允许编辑的字段。
        sysDictionaryType.setTypeName(dictionaryTypeWriteReqDTO.getValue());
        sysDictionaryType.setRemark(dictionaryTypeWriteReqDTO.getRemark());
        sysDictionaryTypeMapper.updateById(sysDictionaryType);
        return sysDictionaryType.getId();
    }

    /**
     * 新增字典数据。
     *
     * @param dictionaryDataAddReqDTO 字典数据新增请求
     * @return 字典数据主键 ID
     */
    @Override
    public Long addData(DictionaryDataAddReqDTO dictionaryDataAddReqDTO) {
        // 1. 字典数据必须挂在一个已存在的字典类型下。
        checkTypeExists(dictionaryDataAddReqDTO.getTypeKey());

        // 2. 校验同一类型下 dataKey 不能重复，对应数据库唯一索引 uk_type_data。
        checkDataNotExists(dictionaryDataAddReqDTO.getTypeKey(), dictionaryDataAddReqDTO.getDataKey(),
                dictionaryDataAddReqDTO.getValue(), null);

        // 3. 将新增请求转换成数据库实体，并补齐默认值。
        SysDictionaryData sysDictionaryData = new SysDictionaryData();
        sysDictionaryData.setTypeKey(dictionaryDataAddReqDTO.getTypeKey());
        sysDictionaryData.setDataKey(dictionaryDataAddReqDTO.getDataKey());
        sysDictionaryData.setValue(dictionaryDataAddReqDTO.getValue());
        sysDictionaryData.setSort(dictionaryDataAddReqDTO.getSort() == null ? DEFAULT_SORT : dictionaryDataAddReqDTO.getSort());
        sysDictionaryData.setStatus(dictionaryDataAddReqDTO.getStatus() == null ? STATUS_ENABLED : dictionaryDataAddReqDTO.getStatus());
        sysDictionaryData.setRemark(dictionaryDataAddReqDTO.getRemark());

        // 4. 写入数据库，返回自增主键。
        sysDictionaryDataMapper.insert(sysDictionaryData);
        return sysDictionaryData.getId();
    }

    /**
     * 分页查询字典数据列表。
     *
     * @param dictionaryDataListReqDTO 字典数据分页查询请求
     * @return 字典数据分页结果
     */
    @Override
    public BasePageVO<DictionaryDataVO> listData(DictionaryDataListReqDTO dictionaryDataListReqDTO) {
        // 1. 组装动态查询条件。
        LambdaQueryWrapper<SysDictionaryData> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StringUtils.hasText(dictionaryDataListReqDTO.getTypeKey()),
                SysDictionaryData::getTypeKey, dictionaryDataListReqDTO.getTypeKey());
        queryWrapper.eq(StringUtils.hasText(dictionaryDataListReqDTO.getDataKey()),
                SysDictionaryData::getDataKey, dictionaryDataListReqDTO.getDataKey());
        queryWrapper.likeRight(StringUtils.hasText(dictionaryDataListReqDTO.getValue()),
                SysDictionaryData::getValue, dictionaryDataListReqDTO.getValue());
        queryWrapper.eq(dictionaryDataListReqDTO.getStatus() != null,
                SysDictionaryData::getStatus, dictionaryDataListReqDTO.getStatus());
        queryWrapper.orderByAsc(SysDictionaryData::getSort);
        queryWrapper.orderByAsc(SysDictionaryData::getId);

        // 2. 执行分页查询。
        Page<SysDictionaryData> page = sysDictionaryDataMapper.selectPage(
                new Page<>(getPageIndex(dictionaryDataListReqDTO.getPageIndex()), getPageSize(dictionaryDataListReqDTO.getPageSize())),
                queryWrapper
        );

        // 3. 将实体转换为接口响应 VO。
        List<DictionaryDataVO> list = new ArrayList<>();
        for (SysDictionaryData sysDictionaryData : page.getRecords()) {
            list.add(toDictionaryDataVO(sysDictionaryData));
        }

        // 4. 统一封装分页响应。
        return toBasePageVO(dictionaryDataListReqDTO.getPageIndex(), dictionaryDataListReqDTO.getPageSize(), page.getTotal(), list);
    }

    /**
     * 编辑字典数据。
     *
     * @param dictionaryDataEditReqDTO 字典数据编辑请求
     * @return 字典数据主键 ID
     */
    @Override
    public Long editData(DictionaryDataEditReqDTO dictionaryDataEditReqDTO) {
        // 1. 校验目标字典类型存在。
        checkTypeExists(dictionaryDataEditReqDTO.getTypeKey());

        // 2. 优先按主键查询原字典数据。
        SysDictionaryData sysDictionaryData = sysDictionaryDataMapper.selectById(dictionaryDataEditReqDTO.getId());
        if (sysDictionaryData == null) {
            // 兼容调试场景：如果误传了字典类型 ID，则按数据库唯一键 typeKey + dataKey 再定位一次。
            sysDictionaryData = sysDictionaryDataMapper.selectOne(
                    new LambdaQueryWrapper<SysDictionaryData>()
                            .eq(SysDictionaryData::getTypeKey, dictionaryDataEditReqDTO.getTypeKey())
                            .eq(SysDictionaryData::getDataKey, dictionaryDataEditReqDTO.getDataKey())
            );
        }
        if (sysDictionaryData == null) {
            throw new ServiceException("字典数据不存在，请先通过 /dictionary_data/list 查询正确的数据 id",
                    ResultCode.INVALID_PARA.getCode());
        }

        // 3. 排除当前记录后，校验同类型下 dataKey 不能重复。
        checkDataNotExists(dictionaryDataEditReqDTO.getTypeKey(), dictionaryDataEditReqDTO.getDataKey(),
                dictionaryDataEditReqDTO.getValue(), sysDictionaryData.getId());

        // 4. 更新允许编辑的字段；未传 sort/status 时保留原值。
        sysDictionaryData.setTypeKey(dictionaryDataEditReqDTO.getTypeKey());
        sysDictionaryData.setDataKey(dictionaryDataEditReqDTO.getDataKey());
        sysDictionaryData.setValue(dictionaryDataEditReqDTO.getValue());
        sysDictionaryData.setSort(dictionaryDataEditReqDTO.getSort() == null ? sysDictionaryData.getSort() : dictionaryDataEditReqDTO.getSort());
        sysDictionaryData.setStatus(dictionaryDataEditReqDTO.getStatus() == null ? sysDictionaryData.getStatus() : dictionaryDataEditReqDTO.getStatus());
        sysDictionaryData.setRemark(dictionaryDataEditReqDTO.getRemark());
        sysDictionaryDataMapper.updateById(sysDictionaryData);
        return sysDictionaryData.getId();
    }

    /**
     * 根据字典类型键查询启用状态的字典数据。
     *
     * @param typeKey 字典类型键
     * @return 字典数据列表
     */
    @Override
    public List<DictionaryDataDTO> selectDictDataByType(String typeKey) {
        // 1. 空 typeKey 没有查询意义，直接返回空列表。
        if (!StringUtils.hasText(typeKey)) {
            return Collections.emptyList();
        }

        // 2. 查询指定类型下启用状态的数据，并按 sort、id 排序。
        List<SysDictionaryData> list = sysDictionaryDataMapper.selectList(
                new LambdaQueryWrapper<SysDictionaryData>()
                        .eq(SysDictionaryData::getTypeKey, typeKey)
                        .eq(SysDictionaryData::getStatus, STATUS_ENABLED)
                        .orderByAsc(SysDictionaryData::getSort)
                        .orderByAsc(SysDictionaryData::getId)
        );

        // 3. 转换成业务侧使用的 DTO。
        return toDictionaryDataDTOList(list);
    }

    /**
     * 根据多个字典类型键查询启用状态的字典数据，并按类型键分组。
     *
     * @param typeKeys 字典类型键列表
     * @return 字典类型键到字典数据列表的映射
     */
    @Override
    public Map<String, List<DictionaryDataDTO>> selectDictDataByTypes(List<String> typeKeys) {
        // 1. 空集合直接返回空 Map，避免 MyBatis-Plus 拼接空 in 条件。
        if (CollectionUtils.isEmpty(typeKeys)) {
            return Collections.emptyMap();
        }

        // 2. 一次性查询多个字典类型下的启用数据。
        List<SysDictionaryData> list = sysDictionaryDataMapper.selectList(
                new LambdaQueryWrapper<SysDictionaryData>()
                        .in(SysDictionaryData::getTypeKey, typeKeys)
                        .eq(SysDictionaryData::getStatus, STATUS_ENABLED)
                        .orderByAsc(SysDictionaryData::getSort)
                        .orderByAsc(SysDictionaryData::getId)
        );

        // 3. 先为每个请求的 typeKey 初始化空列表，保证返回结构稳定。
        Map<String, List<DictionaryDataDTO>> result = new LinkedHashMap<>();
        for (String typeKey : typeKeys) {
            result.put(typeKey, new ArrayList<>());
        }

        // 4. 按 typeKey 将字典数据归类。
        for (SysDictionaryData sysDictionaryData : list) {
            result.computeIfAbsent(sysDictionaryData.getTypeKey(), key -> new ArrayList<>())
                    .add(toDictionaryDataDTO(sysDictionaryData));
        }
        return result;
    }

    /**
     * 根据字典数据业务键查询启用状态的字典数据。
     *
     * @param dataKey 字典数据业务键
     * @return 字典数据，不存在时返回 {@code null}
     */
    @Override
    public DictionaryDataDTO getDictDataByKey(String dataKey) {
        // 1. 空 dataKey 直接返回 null。
        if (!StringUtils.hasText(dataKey)) {
            return null;
        }

        // 2. 按业务键查询启用状态的数据。
        SysDictionaryData sysDictionaryData = sysDictionaryDataMapper.selectOne(
                new LambdaQueryWrapper<SysDictionaryData>()
                        .eq(SysDictionaryData::getDataKey, dataKey)
                        .eq(SysDictionaryData::getStatus, STATUS_ENABLED)
        );

        // 3. 不存在返回 null，存在则转换成 DTO。
        return sysDictionaryData == null ? null : toDictionaryDataDTO(sysDictionaryData);
    }

    /**
     * 根据多个字典数据业务键查询启用状态的字典数据。
     *
     * @param dataKeys 字典数据业务键列表
     * @return 字典数据列表
     */
    @Override
    public List<DictionaryDataDTO> getDictDataByKeys(List<String> dataKeys) {
        // 1. 空集合直接返回空列表。
        if (CollectionUtils.isEmpty(dataKeys)) {
            return Collections.emptyList();
        }

        // 2. 批量查询启用状态的字典数据。
        List<SysDictionaryData> list = sysDictionaryDataMapper.selectList(
                new LambdaQueryWrapper<SysDictionaryData>()
                        .in(SysDictionaryData::getDataKey, dataKeys)
                        .eq(SysDictionaryData::getStatus, STATUS_ENABLED)
                        .orderByAsc(SysDictionaryData::getSort)
                        .orderByAsc(SysDictionaryData::getId)
        );

        // 3. 转换成业务 DTO 列表。
        return toDictionaryDataDTOList(list);
    }

    /**
     * 校验字典类型是否存在。
     *
     * @param typeKey 字典类型键
     */
    private void checkTypeExists(String typeKey) {
        // 根据 typeKey 统计数量，存在即可作为字典数据的归属类型。
        Long count = sysDictionaryTypeMapper.selectCount(
                new LambdaQueryWrapper<SysDictionaryType>()
                        .eq(SysDictionaryType::getTypeKey, typeKey)
        );
        if (count == null || count == 0) {
            throw new ServiceException("字典类型不存在");
        }
    }

    /**
     * 校验字典类型键和名称是否未被占用。
     *
     * @param typeKey 字典类型键
     * @param typeName 字典类型名称
     * @param excludeId 需要排除的主键 ID，新增时传 {@code null}
     */
    private void checkTypeNotExists(String typeKey, String typeName, Long excludeId) {
        // 新增时不排除记录；编辑时排除当前记录，避免误判自己和自己重复。
        LambdaQueryWrapper<SysDictionaryType> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.ne(excludeId != null, SysDictionaryType::getId, excludeId);
        queryWrapper.and(wrapper -> wrapper.eq(SysDictionaryType::getTypeKey, typeKey)
                .or()
                .eq(SysDictionaryType::getTypeName, typeName));

        Long count = sysDictionaryTypeMapper.selectCount(queryWrapper);
        if (count != null && count > 0) {
            throw new ServiceException("字典类型键或者名称已存在");
        }
    }

    /**
     * 校验同一字典类型下的数据业务键是否未被占用。
     *
     * @param typeKey 字典类型键
     * @param dataKey 字典数据业务键
     * @param value 字典数据值，保留参数便于后续扩展同类型展示值校验
     * @param excludeId 需要排除的主键 ID，新增时传 {@code null}
     */
    private void checkDataNotExists(String typeKey, String dataKey, String value, Long excludeId) {
        // 对齐数据库唯一索引 uk_type_data：同一 typeKey 下 dataKey 不能重复。
        Long dataKeyCount = sysDictionaryDataMapper.selectCount(
                new LambdaQueryWrapper<SysDictionaryData>()
                        .ne(excludeId != null, SysDictionaryData::getId, excludeId)
                        .eq(SysDictionaryData::getTypeKey, typeKey)
                        .eq(SysDictionaryData::getDataKey, dataKey)
        );
        if (dataKeyCount != null && dataKeyCount > 0) {
            throw new ServiceException("当前字典类型下字典数据键已存在");
        }
    }

    /**
     * 转换字典类型响应对象。
     *
     * @param sysDictionaryType 字典类型实体
     * @return 字典类型响应对象
     */
    private DictionaryTypeVO toDictionaryTypeVO(SysDictionaryType sysDictionaryType) {
        // 数据库字段叫 typeName，对外接口字段叫 value，这里需要手动映射。
        DictionaryTypeVO dictionaryTypeVO = new DictionaryTypeVO();
        dictionaryTypeVO.setId(sysDictionaryType.getId());
        dictionaryTypeVO.setTypeKey(sysDictionaryType.getTypeKey());
        dictionaryTypeVO.setValue(sysDictionaryType.getTypeName());
        dictionaryTypeVO.setRemark(sysDictionaryType.getRemark());
        dictionaryTypeVO.setStatus(sysDictionaryType.getStatus());
        dictionaryTypeVO.setCreatedAt(sysDictionaryType.getCreatedAt());
        dictionaryTypeVO.setUpdatedAt(sysDictionaryType.getUpdatedAt());
        return dictionaryTypeVO;
    }

    /**
     * 转换字典数据响应对象。
     *
     * @param sysDictionaryData 字典数据实体
     * @return 字典数据响应对象
     */
    private DictionaryDataVO toDictionaryDataVO(SysDictionaryData sysDictionaryData) {
        // 实体字段 value 实际映射数据库 data_value，对外保持 value 字段名。
        DictionaryDataVO dictionaryDataVO = new DictionaryDataVO();
        dictionaryDataVO.setId(sysDictionaryData.getId());
        dictionaryDataVO.setTypeKey(sysDictionaryData.getTypeKey());
        dictionaryDataVO.setDataKey(sysDictionaryData.getDataKey());
        dictionaryDataVO.setValue(sysDictionaryData.getValue());
        dictionaryDataVO.setSort(sysDictionaryData.getSort());
        dictionaryDataVO.setStatus(sysDictionaryData.getStatus());
        dictionaryDataVO.setRemark(sysDictionaryData.getRemark());
        return dictionaryDataVO;
    }

    /**
     * 转换字典数据传输对象。
     *
     * @param sysDictionaryData 字典数据实体
     * @return 字典数据传输对象
     */
    private DictionaryDataDTO toDictionaryDataDTO(SysDictionaryData sysDictionaryData) {
        // DTO 主要给服务内部或远程调用方使用，字段和 VO 基本一致。
        DictionaryDataDTO dictionaryDataDTO = new DictionaryDataDTO();
        dictionaryDataDTO.setId(sysDictionaryData.getId());
        dictionaryDataDTO.setTypeKey(sysDictionaryData.getTypeKey());
        dictionaryDataDTO.setDataKey(sysDictionaryData.getDataKey());
        dictionaryDataDTO.setValue(sysDictionaryData.getValue());
        dictionaryDataDTO.setSort(sysDictionaryData.getSort());
        dictionaryDataDTO.setStatus(sysDictionaryData.getStatus());
        dictionaryDataDTO.setRemark(sysDictionaryData.getRemark());
        return dictionaryDataDTO;
    }

    /**
     * 批量转换字典数据传输对象。
     *
     * @param list 字典数据实体列表
     * @return 字典数据传输对象列表
     */
    private List<DictionaryDataDTO> toDictionaryDataDTOList(List<SysDictionaryData> list) {
        // 查询结果为空时返回空列表，避免调用方出现空指针。
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }

        List<DictionaryDataDTO> result = new ArrayList<>();
        for (SysDictionaryData sysDictionaryData : list) {
            result.add(toDictionaryDataDTO(sysDictionaryData));
        }
        return result;
    }

    /**
     * 构造分页响应对象。
     *
     * @param pageIndex 当前页码
     * @param pageSize 每页数量
     * @param total 总记录数
     * @param list 当前页数据
     * @param <T> 列表数据类型
     * @return 分页响应对象
     */
    private <T> BasePageVO<T> toBasePageVO(Integer pageIndex, Integer pageSize, Long total, List<T> list) {
        // 统一适配项目里的 BasePageVO 字段：pageIndex、pageSize、total、list。
        BasePageVO<T> result = new BasePageVO<>();
        result.setPageIndex((int) getPageIndex(pageIndex));
        result.setPageSize((int) getPageSize(pageSize));
        result.setTotal(total == null ? 0L : total);
        result.setList(list);
        return result;
    }

    /**
     * 获取安全页码。
     *
     * @param pageIndex 请求页码
     * @return 可用于分页查询的页码
     */
    private long getPageIndex(Integer pageIndex) {
        // 页码为空或小于 1 时，统一从第一页开始。
        return pageIndex == null || pageIndex < 1 ? 1L : pageIndex.longValue();
    }

    /**
     * 获取安全分页大小。
     *
     * @param pageSize 请求分页大小
     * @return 可用于分页查询的分页大小
     */
    private long getPageSize(Integer pageSize) {
        // 分页大小为空或非法时使用默认值。
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }

        // 限制最大分页大小，避免接口被大分页拖垮。
        return Math.min(pageSize.longValue(), MAX_PAGE_SIZE);
    }
}
