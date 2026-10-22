package com.anyujia.anyujiagateway.filter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.anyujia.anyujiacommoncore.utils.JsonUtil;
import com.anyujia.anyujiacommoncore.utils.StringUtil;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommonredis.service.RedisService;
import com.anyujia.anyujiagateway.config.IgnoreWhiteProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关统一认证过滤器。
 *
 * <p>负责在请求进入下游服务前完成白名单判断、token 提取、Redis 登录态校验，
 * 并把登录用户信息写入请求头传递给后端服务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    /**
     * 标准认证请求头。
     */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * 兼容自定义 token 请求头。
     */
    private static final String TOKEN_HEADER = "token";

    /**
     * Bearer token 前缀。
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Redis 登录 token key 前缀，需要与 TokenService 写入 Redis 的前缀保持一致。
     */
    private static final String TOKEN_KEY_PREFIX = "login:token:";

    /**
     * 下游服务接收的登录 token 标识请求头。
     */
    private static final String USER_KEY_HEADER = "userKey";

    /**
     * 下游服务接收的用户 ID 请求头。
     */
    private static final String USER_ID_HEADER = "userId";

    /**
     * 下游服务接收的用户名称请求头。
     */
    private static final String USER_NAME_HEADER = "userName";

    /**
     * 下游服务接收的用户来源请求头。
     */
    private static final String USER_FROM_HEADER = "userFrom";

    private static final String USER_TYPE_HEADER = "userType";

    /**
     * 登录用户缓存字段：用户 ID。
     */
    private static final String LOGIN_USER_ID_FIELD = "userId";

    /**
     * 登录用户缓存字段：用户名称。
     */
    private static final String LOGIN_USER_NAME_FIELD = "userName";

    /**
     * 登录用户缓存字段：用户来源。
     */
    private static final String LOGIN_USER_FROM_FIELD = "userFrom";

    private static final String LOGIN_USER_TYPE_FIELD = "userType";

    private static final String CHAT_WEBSOCKET_PATH = "/chat/websocket";
    private static final String WEBSOCKET_TICKET_PARAMETER = "ticket";

    /**
     * 白名单配置。
     */
    private final IgnoreWhiteProperties ignoreWhiteProperties;

    /**
     * Redis 缓存服务。
     */
    private final RedisService redisService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MeterRegistry meterRegistry;

    /**
     * 执行网关认证逻辑。
     *
     * @param exchange 当前请求上下文
     * @param chain 网关过滤器链
     * @return 过滤器执行结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (StringUtil.matches(path, ignoreWhiteProperties.getWhites())) {
            return chain.filter(exchange);
        }

        String webSocketTicket = request.getQueryParams().getFirst(WEBSOCKET_TICKET_PARAMETER);
        if (CHAT_WEBSOCKET_PATH.equals(path) && StringUtils.hasText(webSocketTicket)) {
            return authenticateWebSocketTicket(exchange, chain, webSocketTicket);
        }

        String token = getToken(request);
        if (!StringUtils.hasText(token)) {
            return unauthorizedResponse(exchange, ResultCode.TOKEN_EMPTY);
        }

        long authStarted = System.nanoTime();
        long redisStarted = System.nanoTime();
        Map<String, Object> loginUser = redisService.getCacheObject(getTokenKey(token), new TypeReference<Map<String, Object>>() {
        });
        recordTimer("gateway.auth.redis", System.nanoTime() - redisStarted);
        if (loginUser == null || loginUser.isEmpty()) {
            return unauthorizedResponse(exchange, ResultCode.LOGIN_STATUS_OVERTIME);
        }

        String userId = getStringValue(loginUser, LOGIN_USER_ID_FIELD);
        String userName = getStringValue(loginUser, LOGIN_USER_NAME_FIELD);
        String userFrom = getStringValue(loginUser, LOGIN_USER_FROM_FIELD);
        String userType = getStringValue(loginUser, LOGIN_USER_TYPE_FIELD);
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(userName)) {
            return unauthorizedResponse(exchange, ResultCode.TOKEN_CHECK_FAILED);
        }

        recordTimer("gateway.auth.total", System.nanoTime() - authStarted);
        return forwardAuthenticated(exchange, chain, token, userId, userName, userFrom, userType);
    }

    private void recordTimer(String name, long nanos) {
        if (meterRegistry != null) {
            Timer.builder(name).publishPercentiles(0.95, 0.99).register(meterRegistry)
                    .record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    private Mono<Void> authenticateWebSocketTicket(ServerWebExchange exchange,
                                                    GatewayFilterChain chain,
                                                    String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return unauthorizedResponse(exchange, ResultCode.TOKEN_CHECK_FAILED);
        }
        // The chat service is the authentication authority for the one-time ticket. Keeping the
        // ticket in the query string lets the downstream service consume it atomically. Do not
        // forward client-controlled identity headers; the chat service ignores them and derives
        // the identity exclusively from the consumed ticket.
        ServerHttpRequest.Builder mutate = exchange.getRequest().mutate();
        mutate.headers(headers -> {
            headers.remove(USER_KEY_HEADER);
            headers.remove(USER_ID_HEADER);
            headers.remove(USER_NAME_HEADER);
            headers.remove(USER_FROM_HEADER);
            headers.remove(USER_TYPE_HEADER);
        });
        return chain.filter(exchange.mutate().request(mutate.build()).build());
    }

    private Mono<Void> forwardAuthenticated(ServerWebExchange exchange,
                                            GatewayFilterChain chain,
                                            String token,
                                            String userId,
                                            String userName,
                                            String userFrom,
                                            String userType) {
        ServerHttpRequest.Builder mutate = exchange.getRequest().mutate();
        addHeader(mutate, USER_KEY_HEADER, token);
        addHeader(mutate, USER_ID_HEADER, userId);
        addHeader(mutate, USER_NAME_HEADER, userName);
        addHeader(mutate, USER_FROM_HEADER, userFrom);
        addHeader(mutate, USER_TYPE_HEADER, userType);
        return chain.filter(exchange.mutate().request(mutate.build()).build());
    }

    /**
     * 从请求头中获取 token。
     *
     * @param request 当前请求
     * @return 去掉 Bearer 前缀后的 token
     */
    private String getToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(token)) {
            token = request.getHeaders().getFirst(TOKEN_HEADER);
        }
        if (StringUtils.hasText(token) && token.startsWith(BEARER_PREFIX)) {
            return token.substring(BEARER_PREFIX.length());
        }
        return token;
    }

    /**
     * 生成 Redis 登录态 key。
     *
     * @param token 请求 token
     * @return Redis 登录态 key
     */
    private String getTokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }

    /**
     * 从登录用户缓存中读取字符串字段。
     *
     * @param loginUser 登录用户缓存
     * @param field 字段名
     * @return 字段字符串值
     */
    private String getStringValue(Map<String, Object> loginUser, String field) {
        Object value = loginUser.get(field);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 添加传递给下游服务的请求头。
     *
     * @param mutate 请求构造器
     * @param name 请求头名称
     * @param value 请求头值
     */
    private void addHeader(ServerHttpRequest.Builder mutate, String name, Object value) {
        if (value == null) {
            return;
        }
        mutate.headers(headers -> headers.set(
                name, URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8)));
    }

    /**
     * 写出未认证响应。
     *
     * @param exchange 当前请求上下文
     * @param resultCode 响应码
     * @return 响应写入结果
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, ResultCode resultCode) {
        log.warn("[网关鉴权失败] 请求路径：{}，错误信息：{}",
                exchange.getRequest().getPath(), resultCode.getMsg());
        return writeJsonResponse(exchange.getResponse(), HttpStatus.valueOf(resolveHttpStatus(resultCode)), ApiResult.failure(resultCode));
    }

    /**
     * 从业务响应码解析 HTTP 状态码。
     *
     * @param resultCode 响应码枚举
     * @return HTTP 状态码
     */
    private int resolveHttpStatus(ResultCode resultCode) {
        return Integer.parseInt(String.valueOf(resultCode.getCode()).substring(0, 3));
    }

    /**
     * 写出 JSON 响应。
     *
     * @param response 当前响应
     * @param status HTTP 状态
     * @param result 统一响应体
     * @return 响应写入结果
     */
    private Mono<Void> writeJsonResponse(ServerHttpResponse response, HttpStatus status, ApiResult<?> result) {
        response.setStatusCode(status);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        byte[] responseBytes = JsonUtil.obj2String(result).getBytes(StandardCharsets.UTF_8);
        DataBuffer dataBuffer = response.bufferFactory().wrap(responseBytes);
        return response.writeWith(Mono.just(dataBuffer));
    }

    /**
     * 过滤器执行顺序，认证应早于大部分业务过滤器执行。
     *
     * @return 执行顺序
     */
    @Override
    public int getOrder() {
        return -200;
    }
}
