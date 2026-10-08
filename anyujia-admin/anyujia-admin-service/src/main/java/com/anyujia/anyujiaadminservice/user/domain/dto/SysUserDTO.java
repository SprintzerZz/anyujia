package com.anyujia.anyujiaadminservice.user.domain.dto;

import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * B 端用户新增、编辑和列表响应 DTO。
 */
@Data
public class SysUserDTO {

    /**
     * 用户主键 ID，新增时为空，编辑时必填。
     */
    private Long userId;

    /**
     * 手机号。
     */
    private String phoneNumber;

    /**
     * 密码。
     */
    private String password;

    /**
     * 确认密码。
     */
    private String confirmPassword;

    /**
     * 用户昵称。
     */
    private String nickName;

    /**
     * 用户身份字典键。
     */
    private String identity;

    /**
     * 用户状态字典键。
     */
    private String status;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 校验密码和确认密码是否一致。
     *
     * @return 一致返回 true，否则返回 false
     */
    public boolean checkPassword() {
        return StringUtils.hasText(password) && password.equals(confirmPassword);
    }
}
