package com.anyujia.anyujiacommoncore.utils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.anyujia.anyujiacommondomain.constants.CommonConstants;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON 工具类。
 *
 * 该工具类基于 Jackson ObjectMapper，适合在业务代码中手动进行 JSON 序列化和反序列化。
 *
 * 常见使用场景：
 * 1. 对象转 JSON 字符串。
 * 2. JSON 字符串转对象。
 * 3. JSON 字符串转 List。
 * 4. JSON 字符串转 Map。
 * 5. 日志打印 JSON。
 * 6. Redis 存取对象时做序列化和反序列化。
 * 7. MQ 消息发送和消费时做序列化和反序列化。
 * 8. 手动处理第三方接口返回的 JSON。
 *
 * 注意：
 * Controller 方法中的 @RequestBody JSON 入参转换通常由 Spring 自动完成，不需要手动调用该工具类。
 */
@Slf4j
public final class JsonUtil {

    /**
     * Jackson 核心配置说明：
     * FAIL_ON_UNKNOWN_PROPERTIES=false：反序列化时，忽略 Java 类中不存在的字段。
     * WRITE_DATES_AS_TIMESTAMPS=false：日期序列化时输出格式化字符串，而不是时间戳。
     * FAIL_ON_EMPTY_BEANS=false：空对象序列化时不抛异常。
     * FAIL_ON_INVALID_SUBTYPE=false：遇到无效子类型时不直接失败。
     * WRITE_DATE_KEYS_AS_TIMESTAMPS=false：Map 日期 key 序列化时不输出时间戳。
     * USE_ANNOTATIONS=false：忽略 Jackson 注解，统一使用当前 ObjectMapper 配置。
     * JavaTimeModule：支持 LocalDate、LocalDateTime 等 Java 8 时间类型。
     * NON_NULL：序列化时忽略 null 字段。
     */
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
            .configure(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS, false)
            .configure(MapperFeature.USE_ANNOTATIONS, false)
            .addModule(new JavaTimeModule())
            .defaultDateFormat(new SimpleDateFormat(CommonConstants.STANDARD_FORMAT))
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private JsonUtil() {
    }

    /**
     * 将对象转换为 JSON 字符串。
     *
     * @param obj 待转换对象
     * @param <T> 对象类型
     * @return JSON 字符串；对象为 null 或转换失败时返回 null
     */
    public static <T> String obj2String(T obj) {
        if (obj == null) {
            return null;
        }
        try {
            // 如果是String直接转换成String，如果不是String则使用ObjectMapper转换成String
            return obj instanceof String ? (String) obj : OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Parse Object to String error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将对象转换为格式化后的 JSON 字符串。
     *
     * @param obj 待转换对象
     * @param <T> 对象类型
     * @return 格式化 JSON 字符串；对象为 null 或转换失败时返回 null
     */
    public static <T> String obj2StringPretty(T obj) {
        if (obj == null) {
            return null;
        }
        try {
            return obj instanceof String
                    ? (String) obj
                    : OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Parse Object to Pretty String error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将 JSON 字符串转换为指定类型的对象。
     *
     * 适用转换类型：普通 Java 对象、基础包装类型、String。
     * 示例：User.class、Integer.class、String.class。
     * 不适合直接转换复杂泛型，例如 List<User>、Map<String, User>。
     * 复杂泛型请使用 {@link #string2Obj(String, TypeReference)}。
     *
     * @param str JSON 字符串
     * @param clazz 目标类型
     * @param <T> 目标对象类型
     * @return 目标对象；参数为空或转换失败时返回 null
     */
    @SuppressWarnings("unchecked")
    public static <T> T string2Obj(String str, Class<T> clazz) {
        if (str == null || str.isEmpty() || clazz == null) {
            return null;
        }
        try {
            return clazz.equals(String.class) ? (T) str : OBJECT_MAPPER.readValue(str, clazz);
        } catch (Exception e) {
            log.warn("Parse String to Object error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将 JSON 字符串转换为复杂泛型对象。
     *
     * 适用转换类型：任意复杂泛型结构。
     * 示例：List<User>、Map<String, User>、Map<String, List<User>>。
     * 如果只是简单对象，可使用 {@link #string2Obj(String, Class)}。
     *
     * @param str JSON 字符串
     * @param valueTypeRef 泛型类型引用，例如 new TypeReference<List<User>>() {}
     * @param <T> 目标对象类型
     * @return 目标对象；参数为空或转换失败时返回 null
     */
    public static <T> T string2Obj(String str, TypeReference<T> valueTypeRef) {
        if (str == null || str.isEmpty() || valueTypeRef == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(str, valueTypeRef);
        } catch (Exception e) {
            log.warn("Parse String to Object error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将 JSON 字符串转换为指定元素类型的 List。
     *
     * 适用转换类型：List<T>，其中 T 是普通 Java 对象或基础包装类型。
     * 示例：List<User>、List<Integer>、List<String>。
     * 如果 List 中还嵌套复杂泛型，例如 List<Map<String, User>>，
     * 请使用 {@link #string2Obj(String, TypeReference)}。
     *
     * @param str JSON 数组字符串
     * @param clazz List 元素类型
     * @param <T> 元素类型
     * @return 对象列表；参数为空或转换失败时返回 null
     */
    public static <T> List<T> string2List(String str, Class<T> clazz) {
        if (str == null || str.isEmpty() || clazz == null) {
            return null;
        }
        JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructParametricType(List.class, clazz);
        try {
            return OBJECT_MAPPER.readValue(str, javaType);
        } catch (IOException e) {
            log.warn("Parse String to List error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将 JSON 字符串转换为 Map，key 固定为 String，value 为指定类型。
     *
     * 适用转换类型：Map<String, T>，其中 T 是普通 Java 对象或基础包装类型。
     * 示例：Map<String, User>、Map<String, Integer>、Map<String, String>。
     * 如果 value 仍是复杂泛型，例如 Map<String, List<User>>，
     * 请使用 {@link #string2Obj(String, TypeReference)}。
     *
     * @param str JSON 对象字符串
     * @param valueClass Map value 类型
     * @param <T> Map value 类型
     * @return Map 对象；参数为空或转换失败时返回 null
     */
    public static <T> Map<String, T> string2Map(String str, Class<T> valueClass) {
        if (str == null || str.isEmpty() || valueClass == null) {
            return null;
        }
        JavaType javaType = OBJECT_MAPPER.getTypeFactory()
                .constructMapType(LinkedHashMap.class, String.class, valueClass);
        try {
            return OBJECT_MAPPER.readValue(str, javaType);
        } catch (JsonProcessingException e) {
            log.warn("Parse String to Map error: {}", e.getMessage());
            return null;
        }
    }
}
