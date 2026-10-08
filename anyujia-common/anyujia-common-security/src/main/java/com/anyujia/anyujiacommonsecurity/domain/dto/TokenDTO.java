package com.anyujia.anyujiacommonsecurity.domain.dto;

import lombok.Data;

/**
 * 登录令牌 DTO。
 */
@Data
public class TokenDTO {

    /**
     * 访问令牌。
     */
    private String accessToken;

    /**
     * 令牌类型。
     */
    private String tokenType = "Bearer";

    /**
     * 过期秒数。
     */
    private Long expiresIn;
}
