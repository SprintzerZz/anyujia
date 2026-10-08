package com.anyujia.anyujiacommonsecurity.service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.anyujia.anyujiacommonredis.service.RedisService;
import com.anyujia.anyujiacommonsecurity.domain.dto.LoginUserDTO;
import com.anyujia.anyujiacommonsecurity.domain.dto.TokenDTO;
import com.anyujia.anyujiacommonsecurity.utils.SecurityUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 登录令牌服务。
 *
 * <p>负责创建 token、从请求中解析当前登录用户，以及按用户维度删除登录态。</p>
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    /**
     * Redis token key 前缀。
     */
    private static final String TOKEN_KEY_PREFIX = "login:token:";

    /**
     * 用户 token 索引 key 前缀，用于禁用用户时删除该用户全部登录态。
     */
    private static final String USER_TOKEN_INDEX_PREFIX = "login:user-token:";

    /**
     * 默认 token 过期时间。
     */
    private static final Duration DEFAULT_EXPIRE = Duration.ofHours(2);

    /**
     * Redis 服务。
     */
    private final RedisService redisService;

    /**
     * 当前线程登录用户，主要用于测试或非 Web 调用场景。
     */
    private final ThreadLocal<LoginUserDTO> currentLoginUser = new ThreadLocal<>();

    /**
     * 创建登录令牌并写入 Redis。
     *
     * @param loginUserDTO 登录用户信息
     * @return 令牌信息
     */
    public TokenDTO createToken(LoginUserDTO loginUserDTO) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String redisKey = buildTokenKey(token);
        String userTokenIndexKey = buildUserTokenIndexKey(loginUserDTO.getUserFrom(), loginUserDTO.getUserId());

        redisService.setCacheObject(redisKey, loginUserDTO, DEFAULT_EXPIRE.toSeconds(), TimeUnit.SECONDS);
        redisService.addMember(userTokenIndexKey, redisKey);
        redisService.expire(userTokenIndexKey, DEFAULT_EXPIRE.toSeconds(), TimeUnit.SECONDS);

        TokenDTO tokenDTO = new TokenDTO();
        tokenDTO.setAccessToken(token);
        tokenDTO.setExpiresIn(DEFAULT_EXPIRE.toSeconds());
        return tokenDTO;
    }

    /**
     * 获取当前登录用户。
     *
     * @return 登录用户信息，未登录时返回 {@code null}
     */
    public LoginUserDTO getLoginUser() {
        LoginUserDTO loginUserDTO = currentLoginUser.get();
        if (loginUserDTO != null) {
            return loginUserDTO;
        }

        String token = SecurityUtil.getToken();
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return redisService.getCacheObject(buildTokenKey(token), LoginUserDTO.class);
    }

    /**
     * 设置当前线程登录用户，主要用于测试。
     *
     * @param loginUserDTO 登录用户信息
     */
    public void setLoginUser(LoginUserDTO loginUserDTO) {
        currentLoginUser.set(loginUserDTO);
    }

    /**
     * 删除当前线程登录用户，避免线程复用时残留测试上下文。
     */
    public void clearLoginUser() {
        currentLoginUser.remove();
    }

    /**
     * 删除指定用户的全部登录态。
     *
     * @param userId 用户主键 ID
     * @param userFrom 用户来源
     */
    public void delLoginUser(Long userId, String userFrom) {
        String userTokenIndexKey = buildUserTokenIndexKey(userFrom, userId);
        Set<String> tokenKeys = redisService.getCacheSet(userTokenIndexKey, new TypeReference<Set<String>>() {
        });
        if (!tokenKeys.isEmpty()) {
            redisService.deleteObject(tokenKeys);
        }
        redisService.deleteObject(userTokenIndexKey);
    }

    /**
     * 构造 token Redis key。
     *
     * @param token 客户端访问令牌
     * @return Redis key
     */
    private String buildTokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }

    /**
     * 构造用户 token 索引 Redis key。
     *
     * @param userFrom 用户来源
     * @param userId 用户主键 ID
     * @return 用户 token 索引 Redis key
     */
    private String buildUserTokenIndexKey(String userFrom, Long userId) {
        return USER_TOKEN_INDEX_PREFIX + userFrom + ":" + userId;
    }
}
