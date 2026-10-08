package com.anyujia.anyujiacommoncore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import com.anyujia.anyujiacommoncore.utils.StringUtil;
import org.junit.jupiter.api.Test;

public class StringUtilTests {

    @Test
    void matchesShouldReturnTrueWhenAnyPatternMatches() {
        List<String> patterns = List.of("/admin/**", "/user/*", "/order/info");

        assertThat(StringUtil.matches("/user/info", patterns)).isTrue();
        assertThat(StringUtil.matches("/admin/system/menu", patterns)).isTrue();
        assertThat(StringUtil.matches("/order/info", patterns)).isTrue();
    }

    @Test
    void matchesShouldReturnFalseWhenNoPatternMatches() {
        List<String> patterns = List.of("/admin/**", "/user/*");

        assertThat(StringUtil.matches("/order/info", patterns)).isFalse();
    }

    @Test
    void matchesShouldReturnFalseWhenInputIsEmpty() {
        assertThat(StringUtil.matches("", List.of("/user/**"))).isFalse();
        assertThat(StringUtil.matches("/user/info", Collections.emptyList())).isFalse();
        assertThat(StringUtil.matches("/user/info", null)).isFalse();
    }

    @Test
    void isMatchShouldSupportExactMatch() {
        assertThat(StringUtil.isMatch("/user/info", "/user/info")).isTrue();
        assertThat(StringUtil.isMatch("/user/info", "/user/detail")).isFalse();
    }

    @Test
    void isMatchShouldSupportSingleCharacterWildcard() {
        assertThat(StringUtil.isMatch("/user/?", "/user/1")).isTrue();
        assertThat(StringUtil.isMatch("/user/?", "/user/12")).isFalse();
    }

    @Test
    void isMatchShouldSupportSinglePathWildcard() {
        assertThat(StringUtil.isMatch("/user/*", "/user/info")).isTrue();
        assertThat(StringUtil.isMatch("/user/*", "/user/info/detail")).isFalse();
    }

    @Test
    void isMatchShouldSupportMultiPathWildcard() {
        assertThat(StringUtil.isMatch("/user/**", "/user/info/detail")).isTrue();
    }

    @Test
    void isMatchShouldReturnFalseWhenInputIsEmpty() {
        assertThat(StringUtil.isMatch("", "/user/info")).isFalse();
        assertThat(StringUtil.isMatch("/user/**", "")).isFalse();
        assertThat(StringUtil.isMatch(null, "/user/info")).isFalse();
    }
}
