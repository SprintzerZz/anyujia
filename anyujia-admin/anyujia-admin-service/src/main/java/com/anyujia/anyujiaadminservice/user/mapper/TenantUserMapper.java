package com.anyujia.anyujiaadminservice.user.mapper;

import com.anyujia.anyujiaadminservice.user.domain.entity.TenantUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TenantUserMapper extends BaseMapper<TenantUser> {
    @Insert("""
            INSERT IGNORE INTO tenant_user (app_user_id, status, created_at, updated_at)
            VALUES (#{appUserId}, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)
    int insertEnabledIfAbsent(@Param("appUserId") Long appUserId);

    @Select("""
            SELECT COUNT(1) FROM tenant_user
            WHERE app_user_id = #{appUserId} AND status = 1
            """)
    int countActiveByAppUserId(@Param("appUserId") Long appUserId);
}
