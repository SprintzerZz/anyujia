package com.anyujia.anyujiacouponservice.handler;

import java.util.stream.Collectors;

import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 优惠券服务异常处理器。
 */
@RestControllerAdvice(basePackages = "com.anyujia.anyujiacouponservice")
public class CouponExceptionHandler {

    @ExceptionHandler(ServiceException.class)
    public ApiResult<Void> handleServiceException(ServiceException exception) {
        return ApiResult.failure(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ApiResult.failure(ResultCode.INVALID_PARA.getCode(), message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResult<Void> handleConstraintViolation(ConstraintViolationException exception) {
        return ApiResult.failure(ResultCode.INVALID_PARA.getCode(), exception.getMessage());
    }
}
