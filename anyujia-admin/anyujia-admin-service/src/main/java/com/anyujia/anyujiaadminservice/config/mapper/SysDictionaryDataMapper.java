package com.anyujia.anyujiaadminservice.config.mapper;

import com.anyujia.anyujiaadminservice.config.domain.entity.SysDictionaryData;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典数据表 Mapper 接口。
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper} 后，可直接使用新增、修改、删除、按主键查询、条件查询和分页查询等基础能力。</p>
 */
@Mapper
public interface SysDictionaryDataMapper extends BaseMapper<SysDictionaryData> {
}
