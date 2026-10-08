package com.anyujia.anyujiagateway.handler;

import java.nio.charset.StandardCharsets;

import com.anyujia.anyujiacommoncore.utils.JsonUtil;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关统一异常处理器。
 *
 * Gateway 基于 WebFlux，不能使用 Servlet MVC 的 @RestControllerAdvice 统一处理异常。
 * 这里实现 {@link ErrorWebExceptionHandler}，统一处理网关层异常，并返回项目标准响应结构 {@link ApiResult}。
 */
@Slf4j
@Order(-1)
@Configuration
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    /**
     * 处理网关异常。
     *
     * 如果响应已经提交给客户端，就不能再修改响应体和状态码，此时直接返回原始异常。
     * 如果是服务未找到或路由不存在，返回 404；其他异常统一返回 500。
     *
     * @param exchange ServerWebExchange
     * @param ex 异常信息
     * @return 响应写入结果
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        ApiResult<?> result = resolveExceptionResult(ex);
        int httpCode = resolveHttpStatus(result.getCode());

        log.error("[网关异常处理] 请求路径：{}，异常信息：{}",
                exchange.getRequest().getPath(), ex.getMessage(), ex);

        return writeJsonResponse(response, HttpStatus.valueOf(httpCode), result);
    }

    /**
     * 根据异常类型解析统一响应结果。
     *
     * @param ex 异常信息
     * @return 统一响应结果
     */
    private ApiResult<?> resolveExceptionResult(Throwable ex) {
        if (ex instanceof NotFoundException || isHttpNotFound(ex)) {
            return ApiResult.failure(ResultCode.SERVICE_NOT_FOUND);
        }
        if (ex instanceof ServiceException serviceException) {
            return ApiResult.failure(serviceException.getCode(), serviceException.getMessage());
        }
        return ApiResult.failure(ResultCode.ERROR);
    }

    /**
     * 判断异常是否为 HTTP 404。
     *
     * @param ex 异常信息
     * @return 是否为 HTTP 404
     */
    private boolean isHttpNotFound(Throwable ex) {
        return ex instanceof ResponseStatusException responseStatusException
                && responseStatusException.getStatusCode().value() == HttpStatus.NOT_FOUND.value();
    }

    /**
     * 从业务响应码解析 HTTP 状态码。
     *
     * 业务码前三位约定为 HTTP 状态码，例如 404000 对应 HTTP 404，500000 对应 HTTP 500。
     *
     * @param code 业务响应码
     * @return HTTP 状态码
     */
    private int resolveHttpStatus(int code) {
        String codeText = String.valueOf(code);
        if (codeText.length() < 3) {
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
        return Integer.parseInt(codeText.substring(0, 3));
    }

    /**
     * 写出 JSON 响应。
     *
     * @param response ServerHttpResponse
     * @param status HTTP 状态码
     * @param result 统一响应结果
     * @return 响应写入结果
     */
    private static Mono<Void> writeJsonResponse(
            ServerHttpResponse response,
            HttpStatus status,
            ApiResult<?> result) {
        response.setStatusCode(status);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        byte[] responseBytes = JsonUtil.obj2String(result).getBytes(StandardCharsets.UTF_8);
        DataBuffer dataBuffer = response.bufferFactory().wrap(responseBytes);
        return response.writeWith(Mono.just(dataBuffer));
    }
}
