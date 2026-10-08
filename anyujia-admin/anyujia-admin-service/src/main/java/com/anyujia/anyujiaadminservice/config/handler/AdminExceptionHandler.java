package com.anyujia.anyujiaadminservice.config.handler;

import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 基础管理服务异常处理器。
 *
 * <p>将业务异常和参数校验异常转换为项目统一响应结构，避免接口返回 Spring 默认错误页面。</p>
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.anyujia.anyujiaadminservice")
public class AdminExceptionHandler {

    /**
     * 处理业务异常。
     *
     * @param e 业务异常
     * @return 统一失败响应
     */
    @ExceptionHandler(ServiceException.class)
    public ApiResult<Void> handleServiceException(ServiceException e) {
        log.warn("基础管理服务业务异常，code={}, message={}", e.getCode(), e.getMessage());
        return ApiResult.failure(e.getCode(), e.getMessage());
    }

    /**
     * 处理请求体参数校验异常。
     *
     * @param e 参数校验异常
     * @return 统一失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("基础管理服务请求体参数校验失败，message={}", message);
        return ApiResult.failure(ResultCode.INVALID_PARA.getCode(), message);
    }

    /**
     * 处理查询参数校验异常。
     *
     * @param e 查询参数校验异常
     * @return 统一失败响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResult<Void> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("基础管理服务查询参数校验失败，message={}", e.getMessage());
        return ApiResult.failure(ResultCode.INVALID_PARA.getCode(), e.getMessage());
    }
}
