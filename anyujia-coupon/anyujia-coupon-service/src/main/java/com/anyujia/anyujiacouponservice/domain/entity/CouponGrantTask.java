package com.anyujia.anyujiacouponservice.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("coupon_grant_task")
public class CouponGrantTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("template_id")
    private Long templateId;
    @TableField("user_type")
    private Integer userType;
    @TableField("total_count")
    private Integer totalCount;
    @TableField("success_count")
    private Integer successCount;
    @TableField("failure_count")
    private Integer failureCount;
    private Integer status;
    private String remark;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
