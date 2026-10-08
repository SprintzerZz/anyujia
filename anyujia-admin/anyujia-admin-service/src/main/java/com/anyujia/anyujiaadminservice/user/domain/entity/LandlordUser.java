package com.anyujia.anyujiaadminservice.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("landlord_user")
public class LandlordUser {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("app_user_id")
    private Long appUserId;

    @TableField("verify_status")
    private Integer verifyStatus;

    @TableField("status")
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
