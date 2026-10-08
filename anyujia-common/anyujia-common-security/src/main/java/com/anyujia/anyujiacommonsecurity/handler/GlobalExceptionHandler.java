package com.anyujia.anyujiacommonsecurity.handler;

import java.util.List;
import java.util.stream.Collectors;

import com.anyujia.anyujiacommondomain.constants.CommonConstants;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 *
 * 统一捕获 Controller 层抛出的常见异常，并返回项目统一响应结构 {@link ApiResult}。
 * HTTP 状态码会根据业务响应码前三位设置，例如 400000 对应 HTTP 400，500000 对应 HTTP 500。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 根据业务响应码设置 HTTP 响应状态码。
     *
     * 业务码规则约定前三位为 HTTP 状态码，例如：
     * 1. 400000 -> HTTP 400
     * 2. 401001 -> HTTP 401
     * 3. 500000 -> HTTP 500
     *
     * @param response HTTP 响应对象
     * @param errorCode 业务响应码
     */
    private void setResponseCode(HttpServletResponse response, int errorCode) {
        response.setStatus(resolveHttpStatus(errorCode));
    }

    /**
     * 从业务响应码解析 HTTP 状态码。
     *
     * @param errorCode 业务响应码
     * @return HTTP 状态码；业务码格式异常时返回 500
     */
    private int resolveHttpStatus(int errorCode) {
        String errorCodeText = String.valueOf(errorCode);
        if (errorCodeText.length() < 3) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        try {
            return Integer.parseInt(errorCodeText.substring(0, 3));
        } catch (NumberFormatException e) {
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * 处理请求方法不支持异常。
     *
     * 例如接口只支持 GET，请求却使用 POST 时会进入该处理方法。
     *
     * @param e 异常信息
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     * @return 统一异常响应结果
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResult<?> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException e,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestUri = request.getRequestURI();
        log.error("请求地址 '{}', 不支持 '{}' 请求", requestUri, e.getMethod());
        setResponseCode(response, ResultCode.REQUEST_METHOD_NOT_SUPPORTED.getCode());
        return ApiResult.failure(ResultCode.REQUEST_METHOD_NOT_SUPPORTED);
    }

    /**
     * 处理方法参数类型不匹配异常。
     *
     * 例如接口参数需要 Integer，请求传入 abc 时会进入该处理方法。
     *
     * @param e 异常信息
     * @param response HTTP 响应对象
     * @return 统一异常响应结果
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResult<?> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e,
            HttpServletResponse response) {
        log.error("参数类型不匹配，参数名：{}，参数值：{}", e.getName(), e.getValue(), e);
        setResponseCode(response, ResultCode.PARA_TYPE_MISMATCH.getCode());
        return ApiResult.failure(ResultCode.PARA_TYPE_MISMATCH);
    }

    /**
     * 处理静态资源或 URL 不存在异常。
     *
     * Spring MVC 找不到对应资源或路径时会进入该处理方法。
     *
     * @param e 异常信息
     * @param response HTTP 响应对象
     * @return 统一异常响应结果
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResult<?> handleNoResourceFoundException(
            NoResourceFoundException e,
            HttpServletResponse response) {
        log.error("请求地址不存在：{}", e.getResourcePath(), e);
        setResponseCode(response, ResultCode.URL_NOT_FOUND.getCode());
        return ApiResult.failure(ResultCode.URL_NOT_FOUND);
    }

    /**
     * 处理业务异常。
     *
     * 业务代码主动抛出 {@link ServiceException} 时会进入该处理方法。
     * HTTP 状态码会根据业务异常 code 的前三位设置，响应体返回业务异常中的 code 和 message。
     *
     * @param e 业务异常
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     * @return 统一异常响应结果
     */
    @ExceptionHandler(ServiceException.class)
    public ApiResult<?> handleServiceException(
            ServiceException e,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestUri = request.getRequestURI();
        log.error("请求地址 '{}', 发生业务异常", requestUri, e);
        setResponseCode(response, e.getCode());
        return ApiResult.failure(e.getCode(), e.getMessage());
    }

    /**
     * 处理请求体参数校验异常。
     *
     * Controller 方法参数使用 @RequestBody + @Valid 或 @Validated 校验失败时会进入该处理方法。
     * 常见场景：DTO 字段上的 @NotNull、@NotBlank、@Size 等注解校验不通过。
     *
     * @param e 参数校验异常
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     * @return 统一异常响应结果
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<?> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestUri = request.getRequestURI();
        log.error("请求地址 '{}', 发生请求体参数校验异常", requestUri, e);
        setResponseCode(response, ResultCode.INVALID_PARA.getCode());
        String message = joinValidationMessage(e);
        return ApiResult.failure(ResultCode.INVALID_PARA.getCode(), message);
    }

    /**
     * 拼接请求体参数校验异常消息。
     *
     * 多个字段校验失败时，使用 {@link CommonConstants#DEFAULT_DELIMITER} 拼接成一个错误消息。
     *
     * @param e 参数校验异常
     * @return 拼接后的异常消息；没有错误信息时返回空字符串
     */
    private String joinValidationMessage(MethodArgumentNotValidException e) {
        List<ObjectError> allErrors = e.getAllErrors();
        if (CollectionUtils.isEmpty(allErrors)) {
            return "";
        }
        return allErrors.stream()
                .map(ObjectError::getDefaultMessage)
                .collect(Collectors.joining(CommonConstants.DEFAULT_DELIMITER));
    }

    /**
     * 处理普通参数校验异常。
     *
     * Controller 类或方法使用 @Validated，且普通参数上的 @NotNull、@Min、@Max 等注解校验失败时会进入该处理方法。
     *
     * @param e 参数校验异常
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     * @return 统一异常响应结果
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResult<?> handleConstraintViolationException(
            ConstraintViolationException e,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestUri = request.getRequestURI();
        log.error("请求地址 '{}', 发生普通参数校验异常", requestUri, e);
        setResponseCode(response, ResultCode.INVALID_PARA.getCode());
        return ApiResult.failure(ResultCode.INVALID_PARA.getCode(), e.getMessage());
    }

    /**
     * 处理运行时异常。
     *
     * 该方法用于兜底处理业务运行期间抛出的 RuntimeException。
     *
     * @param e 异常信息
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     * @return 统一异常响应结果
     */
    @ExceptionHandler(RuntimeException.class)
    public ApiResult<?> handleRuntimeException(
            RuntimeException e,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestUri = request.getRequestURI();
        log.error("请求地址 '{}', 发生运行时异常", requestUri, e);
        setResponseCode(response, ResultCode.ERROR.getCode());
        return ApiResult.failure(ResultCode.ERROR);
    }

    /**
     * 处理系统异常。
     *
     * 该方法作为最后兜底，处理未被其他方法捕获的 Exception。
     *
     * @param e 异常信息
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     * @return 统一异常响应结果
     */
    @ExceptionHandler(Exception.class)
    public ApiResult<?> handleException(
            Exception e,
            HttpServletRequest request,
            HttpServletResponse response) {
        String requestUri = request.getRequestURI();
        log.error("请求地址 '{}', 发生系统异常", requestUri, e);
        setResponseCode(response, ResultCode.ERROR.getCode());
        return ApiResult.failure(ResultCode.ERROR);
    }
}
