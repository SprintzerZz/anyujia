package com.anyujia.anyujiacommondomain.domain;

import lombok.Data;

/**
 * 统一接口响应结果。
 *
 * 字段说明：
 * 1. code：业务响应码，对应 {@link ResultCode#getCode()}。
 * 2. msg：响应消息，对应 {@link ResultCode#getMsg()} 或自定义消息。
 * 3. data：响应数据，成功和失败时都可以按业务需要返回。
 *
 * 使用示例：
 * 1. {@code ApiResult.success()}
 * 2. {@code ApiResult.success(data)}
 * 3. {@code ApiResult.failure()}
 * 4. {@code ApiResult.failure("操作失败")}
 *
 * @param <T> 响应数据类型
 */
@Data
public class ApiResult<T> {

    /**
     * 响应码。
     */
    private int code;

    /**
     * 响应消息。
     */
    private String msg;

    /**
     * 响应数据。
     */
    private T data;

    /**
     * 成功响应，不返回业务数据。
     *
     * @param <T> 响应数据类型
     * @return 成功响应结果
     */
    public static <T> ApiResult<T> success() {
        return buildResult(null, ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg());
    }

    /**
     * 成功响应，返回业务数据。
     *
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功响应结果
     */
    public static <T> ApiResult<T> success(T data) {
        return buildResult(data, ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg());
    }

    /**
     * 成功响应，返回业务数据和自定义消息。
     *
     * @param data 响应数据
     * @param msg 自定义响应消息
     * @param <T> 响应数据类型
     * @return 成功响应结果
     */
    public static <T> ApiResult<T> success(T data, String msg) {
        return buildResult(data, ResultCode.SUCCESS.getCode(), msg);
    }

    /**
     * 失败响应，使用默认服务端错误码和默认消息。
     *
     * @param <T> 响应数据类型
     * @return 失败响应结果
     */
    public static <T> ApiResult<T> failure() {
        return buildResult(null, ResultCode.ERROR.getCode(), ResultCode.ERROR.getMsg());
    }

    /**
     * 失败响应，使用默认服务端错误码和自定义消息。
     *
     * @param msg 自定义响应消息
     * @param <T> 响应数据类型
     * @return 失败响应结果
     */
    public static <T> ApiResult<T> failure(String msg) {
        return buildResult(null, ResultCode.ERROR.getCode(), msg);
    }

    /**
     * 失败响应，使用指定响应码枚举。
     *
     * @param resultCode 响应码枚举
     * @param <T> 响应数据类型
     * @return 失败响应结果
     */
    public static <T> ApiResult<T> failure(ResultCode resultCode) {
        return buildResult(null, resultCode.getCode(), resultCode.getMsg());
    }

    /**
     * 失败响应，使用自定义响应码和响应消息。
     *
     * @param code 响应码
     * @param msg 响应消息
     * @param <T> 响应数据类型
     * @return 失败响应结果
     */
    public static <T> ApiResult<T> failure(int code, String msg) {
        return buildResult(null, code, msg);
    }

    /**
     * 失败响应，使用默认服务端错误码，并返回业务数据。
     *
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 失败响应结果
     */
    public static <T> ApiResult<T> failureData(T data) {
        return buildResult(data, ResultCode.ERROR.getCode(), ResultCode.ERROR.getMsg());
    }

    /**
     * 失败响应，使用默认服务端错误码，返回业务数据和自定义消息。
     *
     * @param data 响应数据
     * @param msg 自定义响应消息
     * @param <T> 响应数据类型
     * @return 失败响应结果
     */
    public static <T> ApiResult<T> failureData(T data, String msg) {
        return buildResult(data, ResultCode.ERROR.getCode(), msg);
    }

    /**
     * 构建响应结果。
     *
     * @param data 响应数据
     * @param code 响应码
     * @param msg 响应消息
     * @param <T> 响应数据类型
     * @return 响应结果
     */
    private static <T> ApiResult<T> buildResult(T data, int code, String msg) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }
}
