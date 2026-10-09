package com.anyujia.anyujiacouponservice.mapper;

import com.anyujia.anyujiacouponservice.domain.entity.UserCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户优惠券 Mapper。
 */
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    @Select("select * from user_coupon where id = #{id} for update")
    UserCoupon selectByIdForUpdate(@Param("id") Long id);

    @Select("select * from user_coupon where order_id = #{orderId} for update")
    UserCoupon selectByOrderIdForUpdate(@Param("orderId") Long orderId);

    @Update("""
            update user_coupon
            set order_id = #{orderId},
                service_fee_amount = #{serviceFeeAmount},
                discount_amount = #{discountAmount},
                status = #{targetStatus},
                locked_at = now(),
                updated_at = now()
            where id = #{id}
              and user_id = #{userId}
              and user_type = #{userType}
              and status = #{expectedStatus}
              and order_id is null
            """)
    int lockByStatus(@Param("id") Long id,
                     @Param("userId") Long userId,
                     @Param("userType") Integer userType,
                     @Param("orderId") Long orderId,
                     @Param("serviceFeeAmount") BigDecimal serviceFeeAmount,
                     @Param("discountAmount") BigDecimal discountAmount,
                     @Param("expectedStatus") Integer expectedStatus,
                     @Param("targetStatus") Integer targetStatus);

    @Update("""
            update user_coupon
            set status = #{targetStatus},
                used_at = case when #{targetStatus} = 2 then now() else used_at end,
                updated_at = now()
            where order_id = #{orderId}
              and status = #{expectedStatus}
            """)
    int updateOrderStatus(@Param("orderId") Long orderId,
                          @Param("expectedStatus") Integer expectedStatus,
                          @Param("targetStatus") Integer targetStatus);

    @Update("""
            update user_coupon
            set order_id = null,
                service_fee_amount = null,
                discount_amount = null,
                locked_at = null,
                used_at = null,
                status = #{targetStatus},
                updated_at = now()
            where order_id = #{orderId}
              and status = #{expectedStatus}
            """)
    int clearOrderStatus(@Param("orderId") Long orderId,
                         @Param("expectedStatus") Integer expectedStatus,
                         @Param("targetStatus") Integer targetStatus);

    @Update("""
            update user_coupon
            set status = #{targetStatus},
                updated_at = now()
            where id = #{id}
              and user_id = #{userId}
              and user_type = #{userType}
              and status = #{expectedStatus}
            """)
    int expireUnused(@Param("id") Long id,
                     @Param("userId") Long userId,
                     @Param("userType") Integer userType,
                     @Param("expectedStatus") Integer expectedStatus,
                     @Param("targetStatus") Integer targetStatus);

    @Select("""
            <script>
            select * from user_coupon
            where status = #{unusedStatus}
              and template_id in
            <foreach collection="templateIds" item="tid" open="(" separator="," close=")">
                #{tid}
            </foreach>
            order by id
            limit #{limit}
            </script>
            """)
    List<UserCoupon> selectOverdueUnused(@Param("unusedStatus") Integer unusedStatus,
                                         @Param("templateIds") List<Long> templateIds,
                                         @Param("limit") int limit);

    @Insert("""
            <script>
            insert ignore into user_coupon
                (user_id, user_type, template_id, receive_request_id, status, received_at, updated_at)
            values
            <foreach collection="coupons" item="item" separator=",">
                (#{item.userId}, #{item.userType}, #{item.templateId}, #{item.receiveRequestId},
                 #{item.status}, #{item.receivedAt}, #{item.updatedAt})
            </foreach>
            </script>
            """)
    int insertIgnoreBatch(@Param("coupons") List<UserCoupon> coupons);

    @Select("""
            <script>
            select user_id from user_coupon
            where template_id = #{templateId}
              and user_type = #{userType}
              and user_id in
            <foreach collection="userIds" item="uid" open="(" separator="," close=")">
                #{uid}
            </foreach>
            </script>
            """)
    List<Long> selectExistingUserIds(@Param("templateId") Long templateId,
                                     @Param("userType") Integer userType,
                                     @Param("userIds") List<Long> userIds);
}
