package com.anyujia.anyujiacouponservice.service;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.anyujia.anyujiacouponservice.domain.entity.UserCoupon;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCouponCacheService {

    private static final String USER_COUPON_KEY = "coupon:user-coupon:%d";
    private static final Duration USER_COUPON_TTL = Duration.ofDays(90);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void put(UserCoupon coupon) {
        if (coupon == null || coupon.getId() == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(coupon.getId()), objectMapper.writeValueAsString(coupon), USER_COUPON_TTL);
        } catch (JsonProcessingException ex) {
            log.warn("Serialize user coupon cache failed, userCouponId={}", coupon.getId(), ex);
        }
    }

    public void putBatch(Collection<UserCoupon> coupons) {
        if (coupons == null || coupons.isEmpty()) {
            return;
        }
        RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (UserCoupon coupon : coupons) {
                if (coupon == null || coupon.getId() == null) {
                    continue;
                }
                try {
                    connection.stringCommands().setEx(
                            serializer.serialize(key(coupon.getId())),
                            USER_COUPON_TTL.toSeconds(),
                            serializer.serialize(objectMapper.writeValueAsString(coupon)));
                } catch (JsonProcessingException ex) {
                    log.warn("Serialize user coupon cache failed, userCouponId={}", coupon.getId(), ex);
                }
            }
            return null;
        });
    }

    public Map<Long, UserCoupon> getBatch(Collection<Long> userCouponIds) {
        if (userCouponIds == null || userCouponIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = userCouponIds.stream().distinct().toList();
        RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
        List<Object> values = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long id : ids) {
                connection.stringCommands().get(serializer.serialize(key(id)));
            }
            return null;
        });
        Map<Long, UserCoupon> result = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            Object value = values == null ? null : values.get(i);
            UserCoupon coupon = read(value, ids.get(i));
            if (coupon != null) {
                result.put(ids.get(i), coupon);
            }
        }
        return result;
    }

    public void delete(Long userCouponId) {
        if (userCouponId != null) {
            redisTemplate.delete(key(userCouponId));
        }
    }

    private UserCoupon read(Object value, Long userCouponId) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(text, UserCoupon.class);
        } catch (JsonProcessingException ex) {
            log.warn("Deserialize user coupon cache failed, userCouponId={}", userCouponId, ex);
            return null;
        }
    }

    private String key(Long userCouponId) {
        return USER_COUPON_KEY.formatted(userCouponId);
    }
}
