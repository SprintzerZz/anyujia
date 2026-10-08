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
 * 平台服务费优惠券模板实体。
 */
@Getter
@Setter
@TableName("coupon_template")
public class CouponTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private Integer type;
    @TableField("applicable_user_type")
    private Integer applicableUserType;
    @TableField("scope_type")
    private Integer scopeType;
    @TableField("province_code")
    private String provinceCode;
    @TableField("city_code")
    private String cityCode;
    @TableField("district_code")
    private String districtCode;
    @TableField("policy_code")
    private String policyCode;
    @TableField("grant_mode")
    private Integer grantMode;
    @TableField("receive_limit")
    private Integer receiveLimit;
    @TableField("threshold_amount")
    private BigDecimal thresholdAmount;
    @TableField("discount_amount")
    private BigDecimal discountAmount;
    @TableField("discount_rate")
    private BigDecimal discountRate;
    @TableField("max_discount_amount")
    private BigDecimal maxDiscountAmount;
    @TableField("total_stock")
    private Integer totalStock;
    @TableField("available_stock")
    private Integer availableStock;
    @TableField("valid_start_time")
    private LocalDateTime validStartTime;
    @TableField("valid_end_time")
    private LocalDateTime validEndTime;
    private Integer status;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
