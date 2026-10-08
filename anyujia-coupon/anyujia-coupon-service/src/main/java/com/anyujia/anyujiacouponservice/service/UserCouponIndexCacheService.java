package com.anyujia.anyujiacouponservice.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import com.anyujia.anyujiacouponapi.constants.CouponConstants;
import com.anyujia.anyujiacouponservice.domain.entity.CouponTemplate;
import com.anyujia.anyujiacouponservice.domain.entity.UserCoupon;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserCouponIndexCacheService {

    private static final String UNUSED_INDEX_KEY = "coupon:user:%d:%d:unused";
    private static final Duration INDEX_TTL = Duration.ofDays(90);

    private final StringRedisTemplate redisTemplate;

    @Value("${coupon.time-zone:Asia/Shanghai}")
    private String timeZone;

    public void addUnused(UserCoupon coupon, CouponTemplate template) {
        if (coupon.getId() == null || template == null
                || coupon.getStatus() == null
                || coupon.getStatus() != CouponConstants.USER_COUPON_UNUSED) {
            return;
        }
        redisTemplate.opsForZSet().add(indexKey(coupon.getUserType(), coupon.getUserId()),
                String.valueOf(coupon.getId()), toEpochMillis(template.getValidEndTime()));
        redisTemplate.expire(indexKey(coupon.getUserType(), coupon.getUserId()), INDEX_TTL);
    }

    public void remove(Integer userType, Long userId, Long userCouponId) {
        redisTemplate.opsForZSet().remove(indexKey(userType, userId), String.valueOf(userCouponId));
    }

    public Set<String> currentUnusedIds(Integer userType, Long userId) {
        return currentUnusedIds(userType, userId, 100);
    }

    public Set<String> currentUnusedIds(Integer userType, Long userId, long limit) {
        String key = indexKey(userType, userId);
        return redisTemplate.opsForZSet().rangeByScore(key, System.currentTimeMillis(), Double.POSITIVE_INFINITY,
                0, limit);
    }

    public void addUnusedBatch(List<UserCoupon> coupons, CouponTemplate template) {
        if (template == null || coupons == null || coupons.isEmpty()) {
            return;
        }
        RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (UserCoupon coupon : coupons) {
                if (coupon.getId() == null || coupon.getStatus() == null
                        || coupon.getStatus() != CouponConstants.USER_COUPON_UNUSED) {
                    continue;
                }
                String key = indexKey(coupon.getUserType(), coupon.getUserId());
                connection.zSetCommands().zAdd(serializer.serialize(key),
                        toEpochMillis(template.getValidEndTime()),
                        serializer.serialize(String.valueOf(coupon.getId())));
                connection.keyCommands().expire(serializer.serialize(key), INDEX_TTL.toSeconds());
            }
            return null;
        });
    }

    private String indexKey(Integer userType, Long userId) {
        return UNUSED_INDEX_KEY.formatted(userType, userId);
    }

    private double toEpochMillis(LocalDateTime time) {
        return time.atZone(ZoneId.of(timeZone)).toInstant().toEpochMilli();
    }
}
