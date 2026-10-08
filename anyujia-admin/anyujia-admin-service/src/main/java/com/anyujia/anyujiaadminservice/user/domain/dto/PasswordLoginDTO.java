package com.anyujia.anyujiaadminservice.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * B 端用户账号密码登录请求 DTO。
 */
@Data
public class PasswordLoginDTO {

    /**
     * 手机号。
     */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /**
     * 密码。
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
