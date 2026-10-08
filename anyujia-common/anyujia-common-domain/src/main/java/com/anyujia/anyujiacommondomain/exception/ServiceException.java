package com.anyujia.anyujiacommondomain.exception;

import com.anyujia.anyujiacommondomain.domain.ResultCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 业务异常。
 *
 * 用于业务代码主动抛出可预期异常，例如参数校验不通过、业务状态不允许、第三方服务返回失败等。
 * 推荐优先使用 {@link #ServiceException(ResultCode)}，让异常码和异常消息从统一响应码枚举中获取。
 */
@Getter
@Setter
public class ServiceException extends RuntimeException {

    /**
     * 错误码。
     */
    private int code;

    /**
     * 错误提示。
     */
    private String message;

    /**
     * 根据统一响应码构造业务异常。
     *
     * 推荐使用方式：
     * {@code throw new ServiceException(ResultCode.INVALID_PARA);}
     *
     * @param resultCode 响应码枚举
     */
    public ServiceException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
        this.message = resultCode.getMsg();
    }

    /**
     * 根据自定义错误消息构造业务异常。
     *
     * 错误码默认使用 {@link ResultCode#ERROR}。
     *
     * @param message 异常消息
     */
    public ServiceException(String message) {
        super(message);
        this.code = ResultCode.ERROR.getCode();
        this.message = message;
    }

    /**
     * 根据自定义错误消息和错误码构造业务异常。
     *
     * @param message 异常消息
     * @param code 响应码
     */
    public ServiceException(String message, int code) {
        super(message);
        this.code = code;
        this.message = message;
    }
}
