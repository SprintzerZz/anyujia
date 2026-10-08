package com.anyujia.anyujiaadminservice.user.mapper;

import java.util.List;

import com.anyujia.anyujiaadminservice.user.domain.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * B 端系统用户 Mapper。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据手机号查询用户。
     *
     * @param phoneNumber 手机号
     * @return 用户实体
     */
    @Select("""
            select *
            from sys_user
            where phone_number = #{phoneNumber}
            limit 1
            """)
    SysUser selectByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    /**
     * 按条件查询 B 端用户列表。
     *
     * @param query 查询条件
     * @return 用户列表
     */
    @Select("""
            <script>
            select *
            from sys_user
            <where>
                <if test="query.id != null">
                    and id = #{query.id}
                </if>
                <if test="query.status != null and query.status != ''">
                    and status = #{query.status}
                </if>
                <if test="query.phoneNumber != null and query.phoneNumber != ''">
                    and phone_number = #{query.phoneNumber}
                </if>
            </where>
            order by id desc
            </script>
            """)
    List<SysUser> selectUserList(@Param("query") SysUser query);
}
