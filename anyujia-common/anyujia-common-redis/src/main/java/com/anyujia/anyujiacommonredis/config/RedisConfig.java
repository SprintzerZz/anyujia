package com.anyujia.anyujiacommonredis.config;

import java.text.SimpleDateFormat;

import com.anyujia.anyujiacommondomain.constants.CommonConstants;
import com.anyujia.anyujiacommonredis.service.RedisService;
import com.anyujia.anyujiacommonredis.service.RedissonLockService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.redisson.api.RedissonClient;

/**
 * Redis 配置。
 *
 * <p>统一配置项目中的 {@link RedisTemplate} 序列化方式，避免使用 JDK 默认序列化导致
 * Redis 中 key/value 可读性差、跨语言访问不方便等问题。</p>
 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass({RedisTemplate.class, RedisConnectionFactory.class})
@ConditionalOnBean(RedisConnectionFactory.class)
public class RedisConfig {

    /**
     * 配置 Redis 操作模板。
     *
     * <p>key 和 hash key 使用字符串序列化；value 和 hash value 使用 Jackson JSON 序列化，
     * 支持普通对象、集合、Map 以及 Java 8 日期时间类型。</p>
     *
     * @param redisConnectionFactory Redis 连接工厂，由 Spring Boot 根据 spring.data.redis.* 配置自动创建
     * @return Redis 操作模板
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = "jsonRedisTemplate")
    public RedisTemplate<String, Object> jsonRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jacksonSerializer = createJacksonSerializer();

        redisTemplate.setKeySerializer(stringRedisSerializer);
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        redisTemplate.setValueSerializer(jacksonSerializer);
        redisTemplate.setHashValueSerializer(jacksonSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    /**
     * 注册 Redis 封装服务。
     *
     * <p>业务模块引入 {@code anyujia-common-redis} 后，可以直接注入 {@link RedisService}
     * 使用统一封装的 Redis 操作方法。</p>
     *
     * @param redisTemplate Redis 操作模板
     * @return Redis 封装服务
     */
    @Bean
    @ConditionalOnBean(name = "jsonRedisTemplate")
    @ConditionalOnMissingBean
    public RedisService redisService(@Qualifier("jsonRedisTemplate") RedisTemplate<String, Object> jsonRedisTemplate) {
        return new RedisService(jsonRedisTemplate);
    }

    /**
     * 注册 Redisson 分布式锁服务。
     *
     * <p>当业务项目引入 Redisson 并成功创建 {@link RedissonClient} 后，自动注册
     * {@link RedissonLockService}，用于统一处理分布式锁的获取和释放。</p>
     *
     * @param redissonClient Redisson 客户端
     * @return Redisson 分布式锁服务
     */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean
    public RedissonLockService redissonLockService(RedissonClient redissonClient) {
        return new RedissonLockService(redissonClient);
    }

    /**
     * 创建 Jackson JSON 序列化器。
     *
     * <p>这里统一日期格式为 {@link CommonConstants#STANDARD_FORMAT}，并注册
     * {@link JavaTimeModule} 处理 LocalDate、LocalDateTime 等 JSR-310 时间类型。</p>
     *
     * @return Redis JSON 序列化器
     */
    public GenericJackson2JsonRedisSerializer createJacksonSerializer() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                .configure(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS, false)
                .defaultDateFormat(new SimpleDateFormat(CommonConstants.STANDARD_FORMAT))
                .configure(MapperFeature.USE_ANNOTATIONS, false)
                .addModule(new JavaTimeModule())
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}
