package com.anyujia.anyujiacommondomain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.anyujia.anyujiacommondomain.constants.CommonConstants;
import org.junit.jupiter.api.Test;

public class CommonConstantsTests {

    @Test
    void shouldDefineCommonConstants() {
        assertEquals("yyyy-MM-dd HH:mm:ss", CommonConstants.STANDARD_FORMAT);
        assertEquals("UTF-8", CommonConstants.UTF8);
        assertEquals(", ", CommonConstants.DEFAULT_DELIMITER);
        assertEquals("", CommonConstants.EMPTY_STR);
    }
}
