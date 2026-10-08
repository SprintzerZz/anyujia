package com.anyujia.anyujiamstemplateservice.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.anyujia.anyujiacommoncore.utils.JsonUtil;
import com.anyujia.anyujiacommonredis.service.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/redis")
public class RedisConfigCheckController {

    private final Environment environment;
    private final RedisConnectionFactory redisConnectionFactory;
    private final RedisService redisService;

    public RedisConfigCheckController(
            Environment environment,
            RedisConnectionFactory redisConnectionFactory,
            RedisService redisService) {
        this.environment = environment;
        this.redisConnectionFactory = redisConnectionFactory;
        this.redisService = redisService;
    }

    @GetMapping("/config")
    public Map<String, Object> checkRedisConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("time", LocalDateTime.now().toString());
        result.put("application", environment.getProperty("spring.application.name"));
        result.put("activeProfiles", environment.getActiveProfiles());
        result.put("redisConfig", buildRedisConfig());

        String key = "test:anyujia-mstemplate:redis:" + UUID.randomUUID();
        String value = "ok-" + System.currentTimeMillis();

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            redisService.setCacheObject(key, value, 30L, TimeUnit.SECONDS);
            String readValue = redisService.getCacheObject(key, String.class);
            boolean deleted = redisService.deleteObject(key);

            result.put("success", value.equals(readValue));
            result.put("ping", pong);
            result.put("testKey", key);
            result.put("writeValue", value);
            result.put("readValue", readValue);
            result.put("deleted", deleted);
        } catch (Exception ex) {
            result.put("success", false);
            result.put("errorClass", ex.getClass().getName());
            result.put("errorMessage", ex.getMessage());
        }

        return result;
    }

    @GetMapping("/write")
    public Map<String, Object> writeRedisData(
            @RequestParam(name = "key", defaultValue = "test:anyujia-mstemplate:redis:manual") String key,
            @RequestParam(name = "value", required = false) String value,
            @RequestParam(name = "ttlSeconds", defaultValue = "600") long ttlSeconds) {
        Map<String, Object> result = new LinkedHashMap<>();
        String writeValue = value == null ? "manual-ok-" + System.currentTimeMillis() : value;

        result.put("time", LocalDateTime.now().toString());
        result.put("redisConfig", buildRedisConfig());
        result.put("key", key);
        result.put("writeValue", writeValue);
        result.put("ttlSeconds", ttlSeconds);

        try {
            redisService.setCacheObject(key, writeValue, ttlSeconds, TimeUnit.SECONDS);
            String readValue = redisService.getCacheObject(key, String.class);
            Long expire = redisService.getExpire(key);

            result.put("success", writeValue.equals(readValue));
            result.put("readValue", readValue);
            result.put("expireSeconds", expire);
        } catch (Exception ex) {
            result.put("success", false);
            result.put("errorClass", ex.getClass().getName());
            result.put("errorMessage", ex.getMessage());
        }

        return result;
    }

    @GetMapping("/json")
    public Map<String, Object> writeJsonDataByGet(
            @RequestParam(name = "key", defaultValue = "test:anyujia-mstemplate:redis:json") String key,
            @RequestParam(name = "json", defaultValue = "{\"name\":\"anyujia\",\"type\":\"redis-json\",\"enabled\":true}") String json,
            @RequestParam(name = "ttlSeconds", defaultValue = "600") long ttlSeconds) {
        return writeJsonData(key, json, ttlSeconds);
    }

    @PostMapping("/json")
    public Map<String, Object> writeJsonDataByPost(
            @RequestParam(name = "key", defaultValue = "test:anyujia-mstemplate:redis:json") String key,
            @RequestParam(name = "ttlSeconds", defaultValue = "600") long ttlSeconds,
            @RequestBody String json) {
        return writeJsonData(key, json, ttlSeconds);
    }

    private Map<String, Object> writeJsonData(String key, String json, long ttlSeconds) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("time", LocalDateTime.now().toString());
        result.put("redisConfig", buildRedisConfig());
        result.put("key", key);
        result.put("inputJson", json);
        result.put("ttlSeconds", ttlSeconds);

        Map<String, Object> expectedObject = JsonUtil.string2Obj(json, new TypeReference<LinkedHashMap<String, Object>>() {
        });
        if (expectedObject == null) {
            result.put("success", false);
            result.put("errorMessage", "Invalid JSON object");
            return result;
        }

        try {
            redisService.setCacheObject(key, expectedObject, ttlSeconds, TimeUnit.SECONDS);

            Map<String, Object> readObject = redisService.getCacheObject(
                    key, new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            String rawJson = readRawRedisValue(key);
            Map<String, Object> rawObject = JsonUtil.string2Obj(
                    rawJson, new TypeReference<LinkedHashMap<String, Object>>() {
                    });

            String expectedJson = JsonUtil.obj2String(expectedObject);
            String readJson = JsonUtil.obj2String(readObject);
            String normalizedRawJson = JsonUtil.obj2String(rawObject);

            result.put("success", expectedJson != null
                    && expectedJson.equals(readJson)
                    && expectedJson.equals(normalizedRawJson));
            result.put("expectedJson", expectedJson);
            result.put("readJson", readJson);
            result.put("rawRedisValue", rawJson);
            result.put("normalizedRawRedisJson", normalizedRawJson);
            result.put("expireSeconds", redisService.getExpire(key));
        } catch (Exception ex) {
            result.put("success", false);
            result.put("errorClass", ex.getClass().getName());
            result.put("errorMessage", ex.getMessage());
        }

        return result;
    }

    private Map<String, Object> buildRedisConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("spring.data.redis.host", environment.getProperty("spring.data.redis.host"));
        config.put("spring.data.redis.port", environment.getProperty("spring.data.redis.port"));
        config.put("spring.data.redis.database", environment.getProperty("spring.data.redis.database"));
        config.put("spring.data.redis.username", environment.getProperty("spring.data.redis.username"));
        config.put("spring.data.redis.password", mask(environment.getProperty("spring.data.redis.password")));
        config.put("spring.data.redis.timeout", environment.getProperty("spring.data.redis.timeout"));
        config.put("spring.redis.host", environment.getProperty("spring.redis.host"));
        config.put("spring.redis.port", environment.getProperty("spring.redis.port"));
        config.put("spring.redis.database", environment.getProperty("spring.redis.database"));
        config.put("spring.redis.password", mask(environment.getProperty("spring.redis.password")));
        return config;
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() <= 2) {
            return "**";
        }
        return value.charAt(0) + "***" + value.charAt(value.length() - 1);
    }

    private String readRawRedisValue(String key) {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            byte[] value = connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8));
            return value == null ? null : new String(value, StandardCharsets.UTF_8);
        }
    }
}
