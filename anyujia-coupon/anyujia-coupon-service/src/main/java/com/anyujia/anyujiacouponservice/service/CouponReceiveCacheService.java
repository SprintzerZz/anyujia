package com.anyujia.anyujiacouponservice.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.anyujia.anyujiacouponservice.domain.entity.CouponTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CouponReceiveCacheService {

    private static final String TEMPLATE_KEY = "coupon:template:{%d}";
    private static final String RECEIVE_KEY = "coupon:receive:{%d}:%d:%d";
    private static final Duration CACHE_GRACE_PERIOD = Duration.ofHours(1);

    private static final DefaultRedisScript<Long> INITIALIZE_SCRIPT = script(
            "lua/coupon_template_initialize.lua");
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = script(
            "lua/coupon_receive_reserve.lua");
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = script(
            "lua/coupon_receive_rollback.lua");
    private static final DefaultRedisScript<Long> INVALIDATE_SCRIPT = script(
            "lua/coupon_receive_invalidate.lua");
    private static final DefaultRedisScript<Long> RESTORE_STOCK_KEEP_MARKER_SCRIPT = script(
            "lua/coupon_receive_restore_stock_keep_marker.lua");

    private final StringRedisTemplate redisTemplate;
    private final AtomicLong redisUnavailableUntilMillis = new AtomicLong(0);

    @Value("${coupon.receive.redis.retry-attempts:2}")
    private int retryAttempts;

    @Value("${coupon.receive.redis.retry-backoff-millis:30}")
    private long retryBackoffMillis;

    @Value("${coupon.receive.redis.fast-fail-millis:3000}")
    private long fastFailMillis;

    public CouponReceiveCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void warm(CouponTemplate template) {
        LocalDateTime now = LocalDateTime.now();
        long ttlMillis = Duration.between(now, template.getValidEndTime())
                .plus(CACHE_GRACE_PERIOD)
                .toMillis();
        if (ttlMillis <= 0) {
            return;
        }
        redisTemplate.execute(
                INITIALIZE_SCRIPT,
                List.of(templateKey(template.getId())),
                String.valueOf(template.getAvailableStock()),
                String.valueOf(template.getStatus()),
                String.valueOf(toEpochMillis(template.getValidStartTime())),
                String.valueOf(toEpochMillis(template.getValidEndTime())),
                String.valueOf(template.getApplicableUserType()),
                String.valueOf(template.getScopeType() == null ? 1 : template.getScopeType()),
                value(template.getProvinceCode()),
                value(template.getCityCode()),
                value(template.getDistrictCode()),
                String.valueOf(ttlMillis));
    }

    public ReserveResult reserve(Long templateId, Integer userType, Long userId,
                                 String provinceCode, String cityCode, String districtCode) {
        if (isRedisFastFailing()) {
            return ReserveResult.REDIS_UNAVAILABLE;
        }
        Long code = executeReserveWithRetry(templateId, userType, userId,
                provinceCode, cityCode, districtCode);
        if (code == null) {
            return ReserveResult.REDIS_UNAVAILABLE;
        }
        return ReserveResult.fromCode(code);
    }

    public ReserveResult reserve(Long templateId, Integer userType, Long userId) {
        return reserve(templateId, userType, userId, null, null, null);
    }

    public void rollback(Long templateId, Integer userType, Long userId) {
        redisTemplate.execute(ROLLBACK_SCRIPT, keys(templateId, userType, userId));
    }

    public void invalidateReservation(Long templateId, Integer userType, Long userId) {
        redisTemplate.execute(INVALIDATE_SCRIPT, keys(templateId, userType, userId));
    }

    public void restoreStockKeepReceivedMarker(Long templateId, Integer userType, Long userId) {
        redisTemplate.execute(RESTORE_STOCK_KEEP_MARKER_SCRIPT, keys(templateId, userType, userId));
    }

    private List<String> keys(Long templateId, Integer userType, Long userId) {
        return List.of(templateKey(templateId), RECEIVE_KEY.formatted(templateId, userType, userId));
    }

    private String templateKey(Long templateId) {
        return TEMPLATE_KEY.formatted(templateId);
    }

    private long toEpochMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String value(String raw) {
        return raw == null ? "" : raw;
    }

    private Long executeReserveWithRetry(Long templateId, Integer userType, Long userId,
                                         String provinceCode, String cityCode, String districtCode) {
        RuntimeException lastFailure = null;
        int maxAttempts = Math.max(0, retryAttempts) + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return redisTemplate.execute(
                        RESERVE_SCRIPT,
                        keys(templateId, userType, userId),
                        String.valueOf(System.currentTimeMillis()),
                        String.valueOf(userType),
                        value(provinceCode),
                        value(cityCode),
                        value(districtCode));
            } catch (RuntimeException ex) {
                if (!isTransientRedisFailure(ex)) {
                    throw ex;
                }
                lastFailure = ex;
                if (attempt < maxAttempts) {
                    sleepBeforeRetry(attempt);
                }
            }
        }
        openRedisFastFailWindow();
        log.warn("Coupon receive Redis reserve failed after {} attempts, templateId={}, userType={}, userId={}, cause={}",
                maxAttempts, templateId, userType, userId,
                lastFailure == null ? "unknown" : lastFailure.toString());
        return null;
    }

    private boolean isRedisFastFailing() {
        return System.currentTimeMillis() < redisUnavailableUntilMillis.get();
    }

    private void openRedisFastFailWindow() {
        long until = System.currentTimeMillis() + Math.max(0, fastFailMillis);
        redisUnavailableUntilMillis.accumulateAndGet(until, Math::max);
    }

    private void sleepBeforeRetry(int attempt) {
        long sleepMillis = Math.max(0, retryBackoffMillis) * attempt;
        if (sleepMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isTransientRedisFailure(RuntimeException ex) {
        if (ex instanceof RedisConnectionFailureException
                || ex instanceof QueryTimeoutException) {
            return true;
        }
        Throwable current = ex;
        while (current != null) {
            String name = current.getClass().getName();
            if (name.contains("RedisConnection")
                    || name.contains("RedisCommandTimeout")
                    || name.contains("RedisTimeout")
                    || name.contains("RedisException")
                    || name.contains("SocketTimeout")
                    || name.contains("ConnectException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }

    public enum ReserveResult {
        SUCCESS(0),
        OUT_OF_STOCK(1),
        ALREADY_RECEIVED(2),
        CACHE_MISSING(3),
        TEMPLATE_UNAVAILABLE(4),
        USER_TYPE_MISMATCH(5),
        REDIS_UNAVAILABLE(6);

        private final long code;

        ReserveResult(long code) {
            this.code = code;
        }

        private static ReserveResult fromCode(Long code) {
            if (code != null) {
                for (ReserveResult result : values()) {
                    if (result.code == code) {
                        return result;
                    }
                }
            }
            throw new IllegalStateException("Unexpected coupon reserve result: " + code);
        }
    }
}
