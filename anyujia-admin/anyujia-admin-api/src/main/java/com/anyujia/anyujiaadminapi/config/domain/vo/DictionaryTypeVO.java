package com.anyujia.anyujiaadminapi.config.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 字典类型响应 VO。
 */
@Data
public class DictionaryTypeVO {

    /**
     * 字典类型主键 ID。
     */
    private Long id;

    /**
     * 字典类型键。
     */
    private String typeKey;

    /**
     * 字典类型值。
     */
    private String value;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 状态：1 启用，0 停用。
     */
    private Integer status;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}
