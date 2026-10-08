package com.anyujia.anyujiacommondomain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import org.junit.jupiter.api.Test;

public class ServiceExceptionTests {

    @Test
    void shouldCreateExceptionByResultCode() {
        ServiceException exception = new ServiceException(ResultCode.INVALID_PARA);

        assertEquals(ResultCode.INVALID_PARA.getCode(), exception.getCode());
        assertEquals(ResultCode.INVALID_PARA.getMsg(), exception.getMessage());
    }

    @Test
    void shouldCreateExceptionByMessage() {
        ServiceException exception = new ServiceException("业务处理失败");

        assertEquals(ResultCode.ERROR.getCode(), exception.getCode());
        assertEquals("业务处理失败", exception.getMessage());
    }

    @Test
    void shouldCreateExceptionByMessageAndCode() {
        ServiceException exception = new ServiceException("自定义错误", 400999);

        assertEquals(400999, exception.getCode());
        assertEquals("自定义错误", exception.getMessage());
    }
}
