package com.anyujia.anyujiacommondomain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import org.junit.jupiter.api.Test;

public class ApiResultTests {

    @Test
    void successShouldReturnDefaultSuccessResult() {
        ApiResult<Void> result = ApiResult.success();

        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        assertEquals(ResultCode.SUCCESS.getMsg(), result.getMsg());
        assertNull(result.getData());
    }

    @Test
    void successShouldReturnData() {
        ApiResult<String> result = ApiResult.success("anyujia");

        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        assertEquals(ResultCode.SUCCESS.getMsg(), result.getMsg());
        assertEquals("anyujia", result.getData());
    }

    @Test
    void successShouldReturnDataAndCustomMessage() {
        ApiResult<String> result = ApiResult.success("anyujia", "查询成功");

        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        assertEquals("查询成功", result.getMsg());
        assertEquals("anyujia", result.getData());
    }

    @Test
    void failureShouldReturnDefaultFailureResult() {
        ApiResult<Void> result = ApiResult.failure();

        assertEquals(ResultCode.ERROR.getCode(), result.getCode());
        assertEquals(ResultCode.ERROR.getMsg(), result.getMsg());
        assertNull(result.getData());
    }

    @Test
    void failureShouldReturnCustomMessage() {
        ApiResult<Void> result = ApiResult.failure("操作失败");

        assertEquals(ResultCode.ERROR.getCode(), result.getCode());
        assertEquals("操作失败", result.getMsg());
        assertNull(result.getData());
    }

    @Test
    void failureShouldReturnResultCodeMessage() {
        ApiResult<Void> result = ApiResult.failure(ResultCode.INVALID_PARA);

        assertEquals(ResultCode.INVALID_PARA.getCode(), result.getCode());
        assertEquals(ResultCode.INVALID_PARA.getMsg(), result.getMsg());
        assertNull(result.getData());
    }

    @Test
    void failureShouldReturnCustomCodeAndMessage() {
        ApiResult<Void> result = ApiResult.failure(400999, "自定义错误");

        assertEquals(400999, result.getCode());
        assertEquals("自定义错误", result.getMsg());
        assertNull(result.getData());
    }

    @Test
    void failureDataShouldReturnData() {
        ApiResult<String> result = ApiResult.failureData("error-detail");

        assertEquals(ResultCode.ERROR.getCode(), result.getCode());
        assertEquals(ResultCode.ERROR.getMsg(), result.getMsg());
        assertEquals("error-detail", result.getData());
    }

    @Test
    void failureDataShouldReturnDataAndCustomMessage() {
        ApiResult<String> result = ApiResult.failureData("error-detail", "业务处理失败");

        assertEquals(ResultCode.ERROR.getCode(), result.getCode());
        assertEquals("业务处理失败", result.getMsg());
        assertEquals("error-detail", result.getData());
    }
}
