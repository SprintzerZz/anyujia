package com.anyujia.anyujiaadminservice.map.cache;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 地图缓存失效消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapCacheInvalidationMessage implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 事件唯一 ID，用于幂等处理。
     */
    private String eventId;

    /**
     * 事件类型。
     */
    private String eventType;

    /**
     * 缓存失效范围。
     */
    private String cacheScope;

    /**
     * 行政区划编码，清理全量城市缓存时允许为空。
     */
    private String regionCode;

    /**
     * 缓存版本号。
     */
    private Long version;

    /**
     * 事件创建时间戳，单位毫秒。
     */
    private Long timestamp;

    /**
     * 需要清理的本地缓存 key。
     */
    private List<String> keys;
}
