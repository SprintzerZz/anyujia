package com.anyujia.anyujiaadminservice.config.mapper;

import com.anyujia.anyujiaadminservice.config.domain.entity.SysArgument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统参数 Mapper。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，提供参数表的基础 CRUD 能力。</p>
 */
@Mapper
public interface SysArgumentMapper extends BaseMapper<SysArgument> {
}
