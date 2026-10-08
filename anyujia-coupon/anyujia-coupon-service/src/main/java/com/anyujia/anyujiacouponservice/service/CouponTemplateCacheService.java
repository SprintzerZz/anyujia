package com.anyujia.anyujiacouponservice.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.anyujia.anyujiacouponservice.domain.entity.CouponTemplate;
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
public class CouponTemplateCacheService {

    private static final String TEMPLATE_KEY = "coupon:template:%d";
    private static final Duration TEMPLATE_TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void put(CouponTemplate template) {
        if (template == null || template.getId() == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(template.getId()), objectMapper.writeValueAsString(template), TEMPLATE_TTL);
        } catch (JsonProcessingException ex) {
            log.warn("Serialize coupon template cache failed, templateId={}", template.getId(), ex);
        }
    }

    public void putBatch(Collection<CouponTemplate> templates) {
        if (templates == null || templates.isEmpty()) {
            return;
        }
        RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (CouponTemplate template : templates) {
                if (template == null || template.getId() == null) {
                    continue;
                }
                try {
                    connection.stringCommands().setEx(
                            serializer.serialize(key(template.getId())),
                            TEMPLATE_TTL.toSeconds(),
                            serializer.serialize(objectMapper.writeValueAsString(template)));
                } catch (JsonProcessingException ex) {
                    log.warn("Serialize coupon template cache failed, templateId={}", template.getId(), ex);
                }
            }
            return null;
        });
    }

    public Map<Long, CouponTemplate> getBatch(Collection<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = templateIds.stream().distinct().toList();
        RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
        List<Object> values = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long id : ids) {
                connection.stringCommands().get(serializer.serialize(key(id)));
            }
            return null;
        });
        Map<Long, CouponTemplate> result = new HashMap<>();
        List<Long> missedIds = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Object value = values == null ? null : values.get(i);
            CouponTemplate template = read(value, ids.get(i));
            if (template == null) {
                missedIds.add(ids.get(i));
            } else {
                result.put(ids.get(i), template);
            }
        }
        return result;
    }

    private CouponTemplate read(Object value, Long templateId) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(text, CouponTemplate.class);
        } catch (JsonProcessingException ex) {
            log.warn("Deserialize coupon template cache failed, templateId={}", templateId, ex);
            return null;
        }
    }

    private String key(Long templateId) {
        return TEMPLATE_KEY.formatted(templateId);
    }
}
