package com.anyujia.anyujiafileservice.constants;

/**
 * OSS 签名常量。
 */
public final class OSSConstants {

    private OSSConstants() {
    }

    /**
     * Policy 过期时间格式，必须使用 UTC 时间。
     */
    public static final String SIGN_EXPIRE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    /**
     * Credential 日期格式。
     */
    public static final String SIGN_DATE_FORMAT = "yyyyMMdd";

    /**
     * OSS 请求时间格式。
     */
    public static final String SIGN_REQUEST_TIME_FORMAT = "yyyyMMdd'T'HHmmss'Z'";

    /**
     * 表单直传签名有效期，单位秒。
     */
    public static final long SIGN_EXPERT = 600L;
}
