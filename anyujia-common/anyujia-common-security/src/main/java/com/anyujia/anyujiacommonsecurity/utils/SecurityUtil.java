package com.anyujia.anyujiacommonsecurity.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 安全工具类。
 */
public final class SecurityUtil {

    /**
     * 标准认证请求头。
     */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * 兼容自定义 token 请求头。
     */
    private static final String TOKEN_HEADER = "token";

    /**
     * Bearer token 前缀。
     */
    private static final String BEARER_PREFIX = "Bearer ";

    private SecurityUtil() {
    }

    /**
     * 从当前请求中获取 token。
     *
     * @return token 信息，没有请求上下文或请求头为空时返回 {@code null}
     */
    public static String getToken() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return getToken(attributes.getRequest());
    }

    /**
     * 根据 request 获取 token。
     *
     * @param request 当前请求
     * @return token 信息，请求为空或请求头为空时返回 {@code null}
     */
    public static String getToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String token = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(token)) {
            token = request.getHeader(TOKEN_HEADER);
        }
        return replaceTokenPrefix(token);
    }

    /**
     * 裁剪 token 前缀。
     *
     * @param token 前端传入的 token，可能带 Bearer 前缀
     * @return 裁剪后的 token
     */
    public static String replaceTokenPrefix(String token) {
        if (StringUtils.hasText(token) && token.startsWith(BEARER_PREFIX)) {
            return token.substring(BEARER_PREFIX.length());
        }
        return token;
    }
}
