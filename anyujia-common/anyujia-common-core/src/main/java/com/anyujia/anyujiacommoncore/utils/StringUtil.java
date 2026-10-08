package com.anyujia.anyujiacommoncore.utils;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.AntPathMatcher;

/**
 * 字符串工具类。
 *  常见工具类org.apache.commons提供
 *  当前主要提供 Ant 风格路径匹配能力，常用于接口白名单、权限路径、网关路径规则等场景。
 */
public final class StringUtil {

    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

    private StringUtil() {
    }

    /**
     * 判断指定字符串是否与匹配规则列表中的任意一个规则匹配。
     *
     * 只要 patternList 中存在一个规则可以匹配 str，就返回 true。
     * str 为空或 patternList 为空时返回 false。
     *
     * @param str 指定字符串，例如 /user/info
     * @param patternList 匹配规则列表，例如 /user/**、/order/*
     * @return 是否存在任意一个匹配规则可以匹配指定字符串
     */
    public static boolean matches(String str, List<String> patternList) {
        if (StringUtils.isEmpty(str) || CollectionUtils.isEmpty(patternList)) {
            return false;
        }
        for (String pattern : patternList) {
            if (isMatch(pattern, str)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 URL 是否与 Ant 风格规则匹配。
     *
     * 匹配规则：
     * 1. 精确匹配：/user/info 只能匹配 /user/info。
     * 2. ? 匹配任意单个字符：/user/? 可以匹配 /user/1，不能匹配 /user/12。
     * 3. * 匹配一层路径内的任意字符串，不可跨层级：/user/* 可以匹配 /user/info，不能匹配 /user/info/detail。
     * 4. ** 匹配任意层路径的任意字符，可跨层级：/user/** 可以匹配 /user/info/detail。
     *
     * 注意：
     * 1. 默认区分大小写。
     * 2. pattern 或 url 为空时返回 false。
     * 3. 该方法只做路径规则匹配，不会校验 URL 是否真实存在。
     *
     * @param pattern 匹配规则，例如 /user/**
     * @param url 需要匹配的 URL，例如 /user/info
     * @return URL 是否与规则匹配
     */
    public static boolean isMatch(String pattern, String url) {
        if (StringUtils.isEmpty(url) || StringUtils.isEmpty(pattern)) {
            return false;
        }
        return ANT_PATH_MATCHER.match(pattern, url);
    }
}
