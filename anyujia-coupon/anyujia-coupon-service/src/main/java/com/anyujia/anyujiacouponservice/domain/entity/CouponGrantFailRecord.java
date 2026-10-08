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
@TableName("coupon_grant_fail_record")
public class CouponGrantFailRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("task_id")
    private Long taskId;
    @TableField("template_id")
    private Long templateId;
    @TableField("user_id")
    private Long userId;
    @TableField("user_type")
    private Integer userType;
    private String reason;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
