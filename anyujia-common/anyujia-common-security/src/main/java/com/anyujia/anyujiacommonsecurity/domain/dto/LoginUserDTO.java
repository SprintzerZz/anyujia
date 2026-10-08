package com.anyujia.anyujiacommonsecurity.domain.dto;

import lombok.Data;

/**
 * 登录用户上下文 DTO。
 */
@Data
public class LoginUserDTO {

    /**
     * 用户主键 ID。
     */
    private Long userId;

    /**
     * 用户名称。
     */
    private String userName;

    /**
     * 用户来源，例如 sys。
     */
    private String userFrom;

    /**
     * C-end active identity: 1 tenant, 2 landlord.
     */
    private Integer userType;
}
