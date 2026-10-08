package com.anyujia.anyujiaadminservice.map.mapper;

import java.util.List;

import com.anyujia.anyujiaadminservice.map.domin.entity.SysRegion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行政区划 Mapper。
 */
@Mapper
public interface RegionMapper extends BaseMapper<SysRegion> {

    /**
     * 查询全部行政区划。
     *
     * @return 行政区划列表
     */
    List<SysRegion> selectAllRegion();
}
