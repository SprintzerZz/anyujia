package com.anyujia.anyujiacouponservice.mapper;

import com.anyujia.anyujiacouponservice.domain.entity.CouponGrantTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CouponGrantTaskMapper extends BaseMapper<CouponGrantTask> {

    @Update("""
            update coupon_grant_task
            set success_count = success_count + #{successCount},
                failure_count = failure_count + #{failureCount},
                updated_at = now()
            where id = #{taskId}
            """)
    int increaseProgress(@Param("taskId") Long taskId,
                         @Param("successCount") int successCount,
                         @Param("failureCount") int failureCount);

    @Update("""
            update coupon_grant_task
            set status = #{status}, remark = #{remark}, updated_at = now()
            where id = #{taskId}
            """)
    int updateStatus(@Param("taskId") Long taskId,
                     @Param("status") Integer status,
                     @Param("remark") String remark);
}
