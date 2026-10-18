package com.anyujia.anyujiacommonredis.service;

import com.anyujia.anyujiacommoncore.utils.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作工具类。
 *
 * <p>该类对 Spring Data Redis 的 {@link RedisTemplate} 做一层业务友好的封装，
 * 统一提供 key、String、List、Set、ZSet、Hash 等常见 Redis 数据结构操作。</p>
 *
 * <p>说明：
 * 1. 写入时直接交给 RedisTemplate 处理序列化；
 * 2. 读取复杂对象时，通过 {@link JsonUtil} 做一次 JSON 转换，降低 LinkedHashMap 转业务对象失败的概率；
 * 3. RedisTemplate 的序列化方式由调用方项目中的 Redis 配置决定。</p>
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class RedisService {

    /**
     * Redis 操作模板。
     *
     * <p>这里使用 {@code RedisTemplate<String, Object>}，统一约束 key 为 String，
     * value 支持普通对象、集合、Map 等类型。</p>
     */
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ************************ 基本操作 ***************************

    /**
     * 设置 key 的有效时间，时间单位默认为秒。
     *
     * @param key Redis 键
     * @param timeout 超时时间，单位：秒
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout) {
        return expire(key, timeout, TimeUnit.SECONDS);
    }

    /**
     * 设置 key 的有效时间，可指定时间单位。
     *
     * @param key Redis 键
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout, final TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /**
     * 获取 key 的剩余有效时间，单位为秒。
     *
     * @param key Redis 键
     * @return > 0：剩余有效时间；-1：key 存在但未设置过期时间；-2：key 不存在
     * @throws IllegalStateException 无法获取过期时间时抛出
     */
    public long getExpire(final String key) {
        Long expire = redisTemplate.getExpire(key);
        if (expire == null) {
            throw new IllegalStateException("Failed to get expiration time for Redis key: " + key);
        }
        return expire;
    }

    /**
     * 判断 key 是否存在。
     *
     * @param key Redis 键
     * @return true=存在；false=不存在
     */
    public Boolean hasKey(final String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 对 Redis 数值执行原子递增。
     *
     * @param key Redis key
     * @return 递增后的值
     */
    public Long increment(final String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    /**
     * 根据 key 匹配模式查找 Redis 中的 key。
     *
     * <p>注意：Redis 的 keys 命令会扫描全部 key，生产环境大 key 空间下应谨慎使用。</p>
     *
     * @param pattern key 匹配模式，例如 {@code user:*}
     * @return 匹配到的 key 集合；没有匹配结果时返回空集合
     */
    public Collection<String> keys(final String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        return keys == null ? Collections.emptySet() : keys;
    }

    /**
     * 重命名 key。
     *
     * @param oldKey 原 key
     * @param newKey 新 key
     */
    public void renameKey(final String oldKey, final String newKey) {
        redisTemplate.rename(oldKey, newKey);
    }

    /**
     * 删除单个 key。
     *
     * @param key Redis 键
     * @return true=删除成功；false=删除失败
     */
    public boolean deleteObject(final String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 批量删除 key。
     *
     * @param collection Redis 键集合
     * @return true=至少删除了一个 key；false=没有删除任何 key
     */
    public boolean deleteObject(final Collection<String> collection) {
        Long count = redisTemplate.delete(collection);
        return count != null && count > 0;
    }

    // ************************ 操作 String 类型 ***************************

    /**
     * 缓存普通对象。
     *
     * <p>Redis 中表现为 String 类型，实际序列化格式取决于 RedisTemplate 的 value serializer。</p>
     *
     * @param key 缓存 key
     * @param value 缓存值
     * @param <T> 对象类型
     */
    public <T> void setCacheObject(final String key, final T value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 缓存普通对象，并设置有效时间。
     *
     * @param key 缓存 key
     * @param value 缓存值
     * @param timeout 超时时间
     * @param timeUnit 时间单位
     * @param <T> 对象类型
     */
    public <T> void setCacheObject(final String key, final T value, final Long timeout, final TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    /**
     * 当 key 不存在时缓存普通对象，并设置有效时间。
     *
     * @param key 缓存 key
     * @param value 缓存值
     * @param timeout 超时时间
     * @param timeUnit 时间单位
     * @param <T> 对象类型
     * @return true=写入成功；false=key 已存在或写入失败
     */
    public <T> Boolean setCacheObjectIfAbsent(
            final String key, final T value, final Long timeout, final TimeUnit timeUnit) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, timeout, timeUnit));
    }

    /**
     * 获取缓存对象，并转换为指定类型。
     *
     * @param key 缓存 key
     * @param clazz 目标类型
     * @param <T> 目标对象类型
     * @return 缓存对象；key 不存在或转换失败时返回 null
     */
    public <T> T getCacheObject(final String key, final Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        return convertObject(value, clazz);
    }

    /**
     * 获取缓存对象，并按复杂泛型类型转换。
     *
     * @param key 缓存 key
     * @param reference 泛型类型模板，例如 {@code new TypeReference<List<User>>() {}}
     * @param <T> 目标对象类型
     * @return 缓存对象；key 不存在或转换失败时返回 null
     */
    public <T> T getCacheObject(final String key, final TypeReference<T> reference) {
        Object value = redisTemplate.opsForValue().get(key);
        return convertObject(value, reference);
    }

    /**
     * Atomically reads and removes a cached object.
     */
    public <T> T getAndDeleteCacheObject(final String key, final Class<T> clazz) {
        Object value = redisTemplate.opsForValue().getAndDelete(key);
        return convertObject(value, clazz);
    }

    // ************************ 操作 List 类型 ***************************

    /**
     * 从右侧批量写入 List 数据。
     *
     * @param key 缓存 key
     * @param dataList 待缓存列表
     * @param <T> 元素类型
     * @return 写入后 Redis 列表长度；写入失败时返回 0
     */
    public <T> long setCacheList(final String key, final List<T> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return 0L;
        }
        // Push elements individually so the list round-trips through getCacheList;
        // passing the List directly hits the varargs overload and stores the whole
        // collection as a single serialized array element.
        Long count = redisTemplate.opsForList().rightPushAll(key, dataList.toArray(new Object[0]));
        return count == null ? 0L : count;
    }

    /**
     * 从 List 左侧插入单个元素。
     *
     * @param key Redis 键
     * @param value 元素值
     * @param <T> 元素类型
     */
    public <T> void leftPushForList(final String key, final T value) {
        redisTemplate.opsForList().leftPush(key, value);
    }

    /**
     * 从 List 右侧插入单个元素。
     *
     * @param key Redis 键
     * @param value 元素值
     * @param <T> 元素类型
     */
    public <T> void rightPushForList(final String key, final T value) {
        redisTemplate.opsForList().rightPush(key, value);
    }

    /**
     * 从 List 左侧弹出一个元素。
     *
     * @param key Redis 键
     */
    public void leftPopForList(final String key) {
        redisTemplate.opsForList().leftPop(key);
    }

    /**
     * 从 List 右侧弹出一个元素。
     *
     * @param key Redis 键
     */
    public void rightPopForList(final String key) {
        redisTemplate.opsForList().rightPop(key);
    }

    /**
     * 移除 List 中第一个匹配的元素。
     *
     * @param key Redis 键
     * @param value 要移除的元素值
     * @param <T> 元素类型
     */
    public <T> void removeForList(final String key, final T value) {
        redisTemplate.opsForList().remove(key, 1L, value);
    }

    /**
     * 移除 List 中所有匹配的元素。
     *
     * @param key Redis 键
     * @param value 要移除的元素值
     * @param <T> 元素类型
     */
    public <T> void removeAllForList(final String key, final T value) {
        redisTemplate.opsForList().remove(key, 0L, value);
    }

    /**
     * 清空指定 key 对应的 List。
     *
     * <p>使用 {@code trim(key, -1, 0)} 保留一个空范围，从而达到清空列表的效果。</p>
     *
     * @param key Redis 键
     */
    public void removeForAllList(final String key) {
        redisTemplate.opsForList().trim(key, -1, 0);
    }

    /**
     * 修改 List 指定下标位置的元素。
     *
     * @param key Redis 键
     * @param index 元素下标
     * @param newValue 新元素值
     * @param <T> 元素类型
     */
    public <T> void setElementAtIndex(final String key, final int index, final T newValue) {
        redisTemplate.opsForList().set(key, index, newValue);
    }

    /**
     * 获取完整 List，并转换为指定元素类型。
     *
     * @param key Redis 键
     * @param clazz 元素类型
     * @param <T> 元素类型
     * @return List 数据；key 不存在时返回空列表
     */
    public <T> List<T> getCacheList(final String key, final Class<T> clazz) {
        List<Object> list = redisTemplate.opsForList().range(key, 0, -1);
        return convertList(list, clazz);
    }

    /**
     * 获取完整 List，并按复杂泛型类型转换。
     *
     * @param key Redis 键
     * @param typeReference List 泛型类型模板
     * @param <T> 元素类型
     * @return List 数据；key 不存在或转换失败时返回空列表
     */
    public <T> List<T> getCacheList(final String key, final TypeReference<List<T>> typeReference) {
        List<Object> list = redisTemplate.opsForList().range(key, 0, -1);
        return convertList(list, typeReference);
    }

    /**
     * 按范围获取 List，并转换为指定元素类型。
     *
     * @param key Redis 键
     * @param start 开始下标
     * @param end 结束下标
     * @param clazz 元素类型
     * @param <T> 元素类型
     * @return List 数据；key 不存在时返回空列表
     */
    public <T> List<T> getCacheListByRange(final String key, final long start, final long end, final Class<T> clazz) {
        List<Object> list = redisTemplate.opsForList().range(key, start, end);
        return convertList(list, clazz);
    }

    /**
     * 按范围获取 List，并按复杂泛型类型转换。
     *
     * @param key Redis 键
     * @param start 开始下标
     * @param end 结束下标
     * @param typeReference List 泛型类型模板
     * @param <T> 元素类型
     * @return List 数据；key 不存在或转换失败时返回空列表
     */
    public <T> List<T> getCacheListByRange(
            final String key, final long start, final long end, final TypeReference<List<T>> typeReference) {
        List<Object> list = redisTemplate.opsForList().range(key, start, end);
        return convertList(list, typeReference);
    }

    /**
     * 获取 List 长度。
     *
     * @param key Redis 键
     * @return List 长度；key 不存在时返回 0
     */
    public long getCacheListSize(final String key) {
        Long size = redisTemplate.opsForList().size(key);
        return size == null ? 0L : size;
    }

    // ************************ 操作 Set 类型 ***************************

    /**
     * 向 Set 添加一个或多个元素。
     *
     * @param key Redis 键
     * @param member 元素数组
     */
    public void addMember(final String key, final Object... member) {
        redisTemplate.opsForSet().add(key, member);
    }

    /**
     * 从 Set 删除一个或多个元素。
     *
     * @param key Redis 键
     * @param member 元素数组
     */
    public void deleteMember(final String key, final Object... member) {
        redisTemplate.opsForSet().remove(key, member);
    }

    /**
     * 获取 Set 数据，并按复杂泛型类型转换。
     *
     * @param key Redis 键
     * @param typeReference Set 泛型类型模板
     * @param <T> 元素类型
     * @return Set 数据；key 不存在或转换失败时返回空集合
     */
    public <T> Set<T> getCacheSet(final String key, final TypeReference<Set<T>> typeReference) {
        Set<Object> data = redisTemplate.opsForSet().members(key);
        Set<T> result = convertObject(data, typeReference);
        return result == null ? Collections.emptySet() : result;
    }

    // ************************ 操作 ZSet 类型 ***************************

    /**
     * 向 ZSet 添加元素。
     *
     * @param key Redis 键
     * @param value 元素值
     * @param seqNo 排序分值
     */
    public void addMemberZSet(final String key, final Object value, final double seqNo) {
        redisTemplate.opsForZSet().add(key, value, seqNo);
    }

    /**
     * 从 ZSet 删除元素。
     *
     * @param key Redis 键
     * @param value 元素值
     */
    public void delMemberZSet(final String key, final Object value) {
        redisTemplate.opsForZSet().remove(key, value);
    }

    /**
     * 根据分值范围删除 ZSet 元素。
     *
     * @param key Redis 键
     * @param minScore 最小分值
     * @param maxScore 最大分值
     */
    public void removeZSetByScore(final String key, final double minScore, final double maxScore) {
        redisTemplate.opsForZSet().removeRangeByScore(key, minScore, maxScore);
    }

    /**
     * Removes ZSet elements by their zero-based rank.
     */
    public void removeZSetByRank(final String key, final long start, final long end) {
        redisTemplate.opsForZSet().removeRange(key, start, end);
    }

    /**
     * 按分值升序获取 ZSet 数据。
     *
     * @param key Redis 键
     * @param typeReference 有序集合泛型类型模板
     * @param <T> 元素类型
     * @return ZSet 数据；key 不存在或转换失败时返回空集合
     */
    public <T> Set<T> getCacheZSet(final String key, final TypeReference<LinkedHashSet<T>> typeReference) {
        Set<Object> data = redisTemplate.opsForZSet().range(key, 0, -1);
        Set<T> result = convertObject(data, typeReference);
        return result == null ? Collections.emptySet() : result;
    }

    /**
     * Reads a rank range from a ZSet in ascending score order.
     */
    public <T> Set<T> getCacheZSetByRange(
            final String key, final long start, final long end,
            final TypeReference<LinkedHashSet<T>> typeReference) {
        Set<Object> data = redisTemplate.opsForZSet().range(key, start, end);
        Set<T> result = convertObject(data, typeReference);
        return result == null ? Collections.emptySet() : result;
    }

    /**
     * 按分值降序获取 ZSet 数据。
     *
     * @param key Redis 键
     * @param typeReference 有序集合泛型类型模板
     * @param <T> 元素类型
     * @return 降序 ZSet 数据；key 不存在或转换失败时返回空集合
     */
    public <T> Set<T> getCacheZSetDesc(final String key, final TypeReference<LinkedHashSet<T>> typeReference) {
        Set<Object> data = redisTemplate.opsForZSet().reverseRange(key, 0, -1);
        Set<T> result = convertObject(data, typeReference);
        return result == null ? Collections.emptySet() : result;
    }

    // ************************ 操作 Hash 类型 ***************************

    /**
     * 缓存 Map 数据到 Redis Hash。
     *
     * @param key Redis 键
     * @param dataMap Map 数据
     * @param <T> value 类型
     */
    public <T> void setCacheMap(final String key, final Map<String, T> dataMap) {
        if (dataMap != null && !dataMap.isEmpty()) {
            redisTemplate.opsForHash().putAll(key, dataMap);
        }
    }

    /**
     * 向 Redis Hash 写入单个字段。
     *
     * @param key Redis 键
     * @param hKey Hash 字段名
     * @param value 字段值
     * @param <T> 字段值类型
     */
    public <T> void setCacheMapValue(final String key, final String hKey, final T value) {
        redisTemplate.opsForHash().put(key, hKey, value);
    }

    /**
     * 删除 Redis Hash 中的单个字段。
     *
     * @param key Redis 键
     * @param hKey Hash 字段名
     * @return true=删除成功；false=字段不存在或删除失败
     */
    public boolean deleteCacheMapValue(final String key, final String hKey) {
        Long count = redisTemplate.opsForHash().delete(key, hKey);
        return count != null && count > 0;
    }

    /**
     * 对 Redis Hash 的某个字段执行原子递增（HINCRBY）。
     *
     * @param key Redis 键
     * @param hKey Hash 字段名
     * @param delta 递增量，可为负数
     * @return 递增后的值；失败时返回 null
     */
    public Long incrementMapValue(final String key, final String hKey, final long delta) {
        return redisTemplate.opsForHash().increment(key, hKey, delta);
    }

    /**
     * 获取 Redis Hash 的全部字段，并按复杂泛型类型转换。
     *
     * @param key Redis 键
     * @param typeReference Map 泛型类型模板
     * @param <T> value 类型
     * @return Hash 对应的 Map；key 不存在或转换失败时返回空 Map
     */
    public <T> Map<String, T> getCacheMap(final String key, final TypeReference<Map<String, T>> typeReference) {
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
        Map<String, T> result = convertObject(data, typeReference);
        return result == null ? Collections.emptyMap() : result;
    }

    /**
     * 获取 Redis Hash 中的单个字段值。
     *
     * <p>该方法不接收目标 Class，因此只做泛型强转；如果需要稳定转换复杂对象，
     * 建议调用方从返回值再通过 JsonUtil 转为目标类型。</p>
     *
     * @param key Redis 键
     * @param hKey Hash 字段名
     * @param <T> 字段值类型
     * @return 字段值；字段不存在时返回 null
     */
    public <T> T getCacheMapValue(final String key, final String hKey) {
        return (T) redisTemplate.opsForHash().get(key, hKey);
    }

    /**
     * 批量获取 Redis Hash 中的多个字段值，并按复杂泛型类型转换。
     *
     * @param key Redis 键
     * @param hKeys Hash 字段名集合
     * @param typeReference List 泛型类型模板
     * @param <T> 元素类型
     * @return 字段值列表；key 不存在或转换失败时返回空列表
     */
    public <T> List<T> getMultiCacheMapValue(
            final String key, final Collection<Object> hKeys, final TypeReference<List<T>> typeReference) {
        List<Object> data = redisTemplate.opsForHash().multiGet(key, hKeys);
        return convertList(data, typeReference);
    }

    /**
     * 将 Redis 返回值转换为指定类型。
     *
     * @param value Redis 返回值
     * @param clazz 目标类型
     * @param <T> 目标类型
     * @return 转换后的对象；值为空或转换失败时返回 null
     */
    private <T> T convertObject(final Object value, final Class<T> clazz) {
        if (value == null || clazz == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        return JsonUtil.string2Obj(JsonUtil.obj2String(value), clazz);
    }

    /**
     * 将 Redis 返回值转换为复杂泛型对象。
     *
     * @param value Redis 返回值
     * @param reference 泛型类型模板
     * @param <T> 目标类型
     * @return 转换后的对象；值为空或转换失败时返回 null
     */
    private <T> T convertObject(final Object value, final TypeReference<T> reference) {
        if (value == null || reference == null) {
            return null;
        }
        return JsonUtil.string2Obj(JsonUtil.obj2String(value), reference);
    }

    /**
     * 将 Redis 返回的 List 转换为指定元素类型列表。
     *
     * @param list Redis 返回的原始 List
     * @param clazz 元素类型
     * @param <T> 元素类型
     * @return 转换后的 List；原始值为空或转换失败时返回空列表
     */
    private <T> List<T> convertList(final List<Object> list, final Class<T> clazz) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> result = JsonUtil.string2List(JsonUtil.obj2String(list), clazz);
        return result == null ? Collections.emptyList() : result;
    }

    /**
     * 将 Redis 返回的 List 转换为复杂泛型列表。
     *
     * @param list Redis 返回的原始 List
     * @param typeReference List 泛型类型模板
     * @param <T> 元素类型
     * @return 转换后的 List；原始值为空或转换失败时返回空列表
     */
    private <T> List<T> convertList(final List<Object> list, final TypeReference<List<T>> typeReference) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> result = JsonUtil.string2Obj(JsonUtil.obj2String(list), typeReference);
        return result == null ? Collections.emptyList() : result;
    }
}
