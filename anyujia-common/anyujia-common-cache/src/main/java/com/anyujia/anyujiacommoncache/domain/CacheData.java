package com.anyujia.anyujiacommoncache.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 逻辑过期缓存数据包装对象。
 *
 * @param <T> 缓存数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheData<T> implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 业务数据。
     */
    private T data;

    /**
     * 缓存版本号，用于防止旧数据回填。
     */
    private Long version;

    /**
     * 逻辑过期时间。
     */
    private LocalDateTime logicalExpireTime;

    /**
     * 判断缓存是否已经逻辑过期。
     *
     * @return true 表示已逻辑过期
     */
    public boolean isLogicalExpired() {
        return logicalExpireTime != null && LocalDateTime.now().isAfter(logicalExpireTime);
    }
}
