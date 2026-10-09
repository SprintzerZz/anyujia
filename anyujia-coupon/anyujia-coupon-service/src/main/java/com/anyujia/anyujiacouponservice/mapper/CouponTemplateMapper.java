package com.anyujia.anyujiacouponservice.mapper;

import com.anyujia.anyujiacouponservice.domain.entity.CouponTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 优惠券模板 Mapper。
 */
public interface CouponTemplateMapper extends BaseMapper<CouponTemplate> {

    @Update("""
            update coupon_template
            set available_stock = available_stock - 1, updated_at = now()
            where id = #{templateId}
              and status = 1
              and (applicable_user_type = 0 or applicable_user_type = #{userType})
              and grant_mode = 1
              and available_stock > 0
              and valid_start_time <= now()
              and valid_end_time > now()
            """)
    int decrementStock(@Param("templateId") Long templateId, @Param("userType") Integer userType);

    @Update("""
            update coupon_template
            set available_stock = available_stock - #{count}, updated_at = now()
            where id = #{templateId}
              and status = 1
              and (applicable_user_type = 0 or applicable_user_type = #{userType})
              and available_stock >= #{count}
              and valid_start_time <= now()
              and valid_end_time > now()
            """)
    int decrementStockBatch(@Param("templateId") Long templateId,
                            @Param("userType") Integer userType,
                            @Param("count") int count);

    @Select("""
            select id from coupon_template
            where valid_end_time <= now()
            """)
    List<Long> selectExpiredTemplateIds();
}
