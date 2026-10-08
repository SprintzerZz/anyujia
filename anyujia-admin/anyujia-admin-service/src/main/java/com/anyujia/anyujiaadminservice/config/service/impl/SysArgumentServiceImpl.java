package com.anyujia.anyujiaadminservice.config.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentAddReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentEditReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentListReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.vo.ArgumentVO;
import com.anyujia.anyujiaadminservice.config.domain.entity.SysArgument;
import com.anyujia.anyujiaadminservice.config.mapper.SysArgumentMapper;
import com.anyujia.anyujiaadminservice.config.service.ISysArgumentService;
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
 * 系统参数服务实现类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysArgumentServiceImpl implements ISysArgumentService {

    /**
     * 启用状态。
     */
    private static final int STATUS_ENABLED = 1;

    /**
     * 系统参数 Mapper。
     */
    private final SysArgumentMapper sysArgumentMapper;

    /**
     * 新增参数。
     *
     * @param argumentAddReqDTO 参数新增请求
     * @return 参数主键 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(ArgumentAddReqDTO argumentAddReqDTO) {
        checkArgumentNotExists(argumentAddReqDTO.getConfigKey(), argumentAddReqDTO.getName(), null);

        SysArgument sysArgument = new SysArgument();
        sysArgument.setName(argumentAddReqDTO.getName());
        sysArgument.setConfigKey(argumentAddReqDTO.getConfigKey());
        sysArgument.setValue(argumentAddReqDTO.getValue());
        sysArgument.setRemark(argumentAddReqDTO.getRemark());
        sysArgument.setStatus(argumentAddReqDTO.getStatus() == null ? STATUS_ENABLED : argumentAddReqDTO.getStatus());

        sysArgumentMapper.insert(sysArgument);
        log.info("新增系统参数成功，configKey={}, id={}", sysArgument.getConfigKey(), sysArgument.getId());
        return sysArgument.getId();
    }

    /**
     * 分页查询参数列表。
     *
     * @param argumentListReqDTO 参数分页查询请求
     * @return 参数分页结果
     */
    @Override
    public BasePageVO<ArgumentVO> list(ArgumentListReqDTO argumentListReqDTO) {
        LambdaQueryWrapper<SysArgument> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StringUtils.hasText(argumentListReqDTO.getConfigKey()),
                SysArgument::getConfigKey, argumentListReqDTO.getConfigKey());
        queryWrapper.likeRight(StringUtils.hasText(argumentListReqDTO.getName()),
                SysArgument::getName, argumentListReqDTO.getName());
        queryWrapper.eq(argumentListReqDTO.getStatus() != null,
                SysArgument::getStatus, argumentListReqDTO.getStatus());
        queryWrapper.orderByDesc(SysArgument::getId);

        Page<SysArgument> page = sysArgumentMapper.selectPage(
                new Page<>(getPageIndex(argumentListReqDTO.getPageIndex()), getPageSize(argumentListReqDTO.getPageSize())),
                queryWrapper
        );

        List<ArgumentVO> list = new ArrayList<>();
        for (SysArgument sysArgument : page.getRecords()) {
            list.add(toArgumentVO(sysArgument));
        }
        return toBasePageVO(argumentListReqDTO.getPageIndex(), argumentListReqDTO.getPageSize(), page.getTotal(), list);
    }

    /**
     * 编辑参数。
     *
     * @param argumentEditReqDTO 参数编辑请求
     * @return 参数主键 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long edit(ArgumentEditReqDTO argumentEditReqDTO) {
        SysArgument sysArgument = sysArgumentMapper.selectOne(
                new LambdaQueryWrapper<SysArgument>()
                        .eq(SysArgument::getConfigKey, argumentEditReqDTO.getConfigKey())
        );
        if (sysArgument == null) {
            throw new ServiceException("参数不存在", ResultCode.INVALID_PARA.getCode());
        }

        checkArgumentNotExists(argumentEditReqDTO.getConfigKey(), argumentEditReqDTO.getName(), sysArgument.getId());

        sysArgument.setName(argumentEditReqDTO.getName());
        sysArgument.setValue(argumentEditReqDTO.getValue());
        sysArgument.setRemark(argumentEditReqDTO.getRemark());
        sysArgument.setStatus(argumentEditReqDTO.getStatus() == null ? sysArgument.getStatus() : argumentEditReqDTO.getStatus());

        sysArgumentMapper.updateById(sysArgument);
        log.info("编辑系统参数成功，configKey={}, id={}", sysArgument.getConfigKey(), sysArgument.getId());
        return sysArgument.getId();
    }

    /**
     * 根据参数键名查询启用状态的参数。
     *
     * @param configKey 参数键名
     * @return 参数信息，不存在时返回 {@code null}
     */
    @Override
    public ArgumentDTO getByConfigKey(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return null;
        }

        SysArgument sysArgument = sysArgumentMapper.selectOne(
                new LambdaQueryWrapper<SysArgument>()
                        .eq(SysArgument::getConfigKey, configKey)
                        .eq(SysArgument::getStatus, STATUS_ENABLED)
        );
        return sysArgument == null ? null : toArgumentDTO(sysArgument);
    }

    /**
     * 根据多个参数键名批量查询启用状态的参数。
     *
     * @param configKeys 参数键名列表
     * @return 参数列表
     */
    @Override
    public List<ArgumentDTO> getByConfigKeys(List<String> configKeys) {
        if (CollectionUtils.isEmpty(configKeys)) {
            return Collections.emptyList();
        }

        List<SysArgument> list = sysArgumentMapper.selectList(
                new LambdaQueryWrapper<SysArgument>()
                        .in(SysArgument::getConfigKey, configKeys)
                        .eq(SysArgument::getStatus, STATUS_ENABLED)
        );
        return toArgumentDTOList(list);
    }

    /**
     * 校验参数键名和参数名称是否未被占用。
     *
     * @param configKey 参数键名
     * @param name 参数名称
     * @param excludeId 需要排除的主键 ID，新增时传 {@code null}
     */
    private void checkArgumentNotExists(String configKey, String name, Long excludeId) {
        LambdaQueryWrapper<SysArgument> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.ne(excludeId != null, SysArgument::getId, excludeId);
        queryWrapper.and(wrapper -> wrapper.eq(SysArgument::getConfigKey, configKey)
                .or()
                .eq(SysArgument::getName, name));

        Long count = sysArgumentMapper.selectCount(queryWrapper);
        if (count != null && count > 0) {
            throw new ServiceException("参数键名或者参数名称已存在", ResultCode.INVALID_PARA.getCode());
        }
    }

    /**
     * 转换参数响应对象。
     *
     * @param sysArgument 参数实体
     * @return 参数响应对象
     */
    private ArgumentVO toArgumentVO(SysArgument sysArgument) {
        ArgumentVO argumentVO = new ArgumentVO();
        argumentVO.setId(sysArgument.getId());
        argumentVO.setName(sysArgument.getName());
        argumentVO.setConfigKey(sysArgument.getConfigKey());
        argumentVO.setValue(sysArgument.getValue());
        argumentVO.setStatus(sysArgument.getStatus());
        argumentVO.setRemark(sysArgument.getRemark());
        argumentVO.setCreatedAt(sysArgument.getCreatedAt());
        argumentVO.setUpdatedAt(sysArgument.getUpdatedAt());
        return argumentVO;
    }

    /**
     * 转换参数数据传输对象。
     *
     * @param sysArgument 参数实体
     * @return 参数数据传输对象
     */
    private ArgumentDTO toArgumentDTO(SysArgument sysArgument) {
        ArgumentDTO argumentDTO = new ArgumentDTO();
        argumentDTO.setId(sysArgument.getId());
        argumentDTO.setName(sysArgument.getName());
        argumentDTO.setConfigKey(sysArgument.getConfigKey());
        argumentDTO.setValue(sysArgument.getValue());
        argumentDTO.setStatus(sysArgument.getStatus());
        argumentDTO.setRemark(sysArgument.getRemark());
        return argumentDTO;
    }

    /**
     * 批量转换参数数据传输对象。
     *
     * @param list 参数实体列表
     * @return 参数数据传输对象列表
     */
    private List<ArgumentDTO> toArgumentDTOList(List<SysArgument> list) {
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }

        List<ArgumentDTO> result = new ArrayList<>();
        for (SysArgument sysArgument : list) {
            result.add(toArgumentDTO(sysArgument));
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
     * @return 安全页码
     */
    private long getPageIndex(Integer pageIndex) {
        return pageIndex == null || pageIndex < 1 ? 1L : pageIndex.longValue();
    }

    /**
     * 获取安全每页数量。
     *
     * @param pageSize 请求每页数量
     * @return 安全每页数量
     */
    private long getPageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10L : pageSize.longValue();
    }
}
