package com.anyujia.anyujiacommonsecurity.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import com.anyujia.anyujiacommondomain.constants.CommonConstants;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

public class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleHttpRequestMethodNotSupportedShouldReturn405Result() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test/info");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("POST", List.of("GET"));

        ApiResult<?> result = handler.handleHttpRequestMethodNotSupported(exception, request, response);

        assertResult(result, ResultCode.REQUEST_METHOD_NOT_SUPPORTED);
        assertEquals(405, response.getStatus());
    }

    @Test
    void handleMethodArgumentTypeMismatchExceptionShouldReturn400Result() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "age", null, new NumberFormatException("For input string: abc"));

        ApiResult<?> result = handler.handleMethodArgumentTypeMismatchException(exception, response);

        assertResult(result, ResultCode.PARA_TYPE_MISMATCH);
        assertEquals(400, response.getStatus());
    }

    @Test
    void handleNoResourceFoundExceptionShouldReturn404Result() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "/missing");

        ApiResult<?> result = handler.handleNoResourceFoundException(exception, response);

        assertResult(result, ResultCode.URL_NOT_FOUND);
        assertEquals(404, response.getStatus());
    }

    @Test
    void handleServiceExceptionShouldReturnBusinessResult() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/business");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServiceException exception = new ServiceException(ResultCode.INVALID_PARA);

        ApiResult<?> result = handler.handleServiceException(exception, request, response);

        assertResult(result, ResultCode.INVALID_PARA);
        assertEquals(400, response.getStatus());
    }

    @Test
    void handleMethodArgumentNotValidExceptionShouldReturnJoinedValidationMessage() throws NoSuchMethodException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MethodArgumentNotValidException exception = createMethodArgumentNotValidException();

        ApiResult<?> result = handler.handleMethodArgumentNotValidException(exception, request, response);

        assertEquals(ResultCode.INVALID_PARA.getCode(), result.getCode());
        assertEquals("用户名不能为空" + CommonConstants.DEFAULT_DELIMITER + "年龄不能为空", result.getMsg());
        assertNull(result.getData());
        assertEquals(400, response.getStatus());
    }

    @Test
    void handleConstraintViolationExceptionShouldReturnValidationMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ConstraintViolationException exception = new ConstraintViolationException("id不能为空", Set.of());

        ApiResult<?> result = handler.handleConstraintViolationException(exception, request, response);

        assertEquals(ResultCode.INVALID_PARA.getCode(), result.getCode());
        assertEquals("id不能为空", result.getMsg());
        assertNull(result.getData());
        assertEquals(400, response.getStatus());
    }

    @Test
    void handleRuntimeExceptionShouldReturn500Result() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/runtime");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResult<?> result = handler.handleRuntimeException(new RuntimeException("runtime error"), request, response);

        assertResult(result, ResultCode.ERROR);
        assertEquals(500, response.getStatus());
    }

    @Test
    void handleExceptionShouldReturn500Result() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResult<?> result = handler.handleException(new Exception("system error"), request, response);

        assertResult(result, ResultCode.ERROR);
        assertEquals(500, response.getStatus());
    }

    private void assertResult(ApiResult<?> result, ResultCode resultCode) {
        assertEquals(resultCode.getCode(), result.getCode());
        assertEquals(resultCode.getMsg(), result.getMsg());
        assertNull(result.getData());
    }

    private MethodArgumentNotValidException createMethodArgumentNotValidException() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTests.class.getDeclaredMethod("validationMethod", ValidationUserDTO.class);
        MethodParameter methodParameter = new MethodParameter(method, 0);
        ValidationUserDTO target = new ValidationUserDTO();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "validationUserDTO");
        bindingResult.addError(new FieldError("validationUserDTO", "username", "用户名不能为空"));
        bindingResult.addError(new FieldError("validationUserDTO", "age", "年龄不能为空"));
        return new MethodArgumentNotValidException(methodParameter, bindingResult);
    }

    @SuppressWarnings("unused")
    private void validationMethod(ValidationUserDTO validationUserDTO) {
    }

    private static class ValidationUserDTO {
    }
}
