package com.anyujia.anyujiacouponservice.domain.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户领取的平台服务费优惠券实体。
 */
@Getter
@Setter
@TableName("user_coupon")
public class UserCoupon implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("user_type")
    private Integer userType;
    @TableField("template_id")
    private Long templateId;
    @TableField("receive_request_id")
    private String receiveRequestId;
    @TableField("order_id")
    private Long orderId;
    @TableField("service_fee_amount")
    private BigDecimal serviceFeeAmount;
    @TableField("discount_amount")
    private BigDecimal discountAmount;
    private Integer status;
    @TableField("received_at")
    private LocalDateTime receivedAt;
    @TableField("locked_at")
    private LocalDateTime lockedAt;
    @TableField("used_at")
    private LocalDateTime usedAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
