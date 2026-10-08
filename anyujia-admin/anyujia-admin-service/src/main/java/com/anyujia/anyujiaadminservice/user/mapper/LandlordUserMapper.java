package com.anyujia.anyujiaadminservice.user.mapper;

import com.anyujia.anyujiaadminservice.user.domain.entity.LandlordUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LandlordUserMapper extends BaseMapper<LandlordUser> {
    @Select("""
            SELECT id, app_user_id, verify_status, status, created_at, updated_at
            FROM landlord_user
            WHERE app_user_id = #{appUserId}
            LIMIT 1
            """)
    LandlordUser selectByAppUserId(@Param("appUserId") Long appUserId);
}
