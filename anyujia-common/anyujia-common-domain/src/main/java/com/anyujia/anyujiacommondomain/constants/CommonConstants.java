package com.anyujia.anyujiacommondomain.constants;

/**
 * 通用常量。
 *
 * 用于存放多个模块都会用到的基础常量，例如日期格式、字符编码、默认分隔符等。
 */
public final class CommonConstants {

    /**
     * 通用日期时间格式。
     *
     * 示例：2026-08-22 16:30:00
     */
    public static final String STANDARD_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 默认字符编码。
     */
    public static final String UTF8 = "UTF-8";

    /**
     * 默认分隔符。
     *
     * 常用于拼接错误消息、日志字段、集合文本等。
     */
    public static final String DEFAULT_DELIMITER = ", ";

    /**
     * 空字符串。
     */
    public static final String EMPTY_STR = "";

    private CommonConstants() {
    }
}
