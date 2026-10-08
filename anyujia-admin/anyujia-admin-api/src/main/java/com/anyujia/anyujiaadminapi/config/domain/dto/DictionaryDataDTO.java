package com.anyujia.anyujiaadminapi.config.domain.dto;

import lombok.Data;

/**
 * 字典数据传输 DTO。
 */
@Data
public class DictionaryDataDTO {

    /**
     * 字典数据主键 ID。
     */
    private Long id;

    /**
     * 字典类型键。
     */
    private String typeKey;

    /**
     * 字典数据业务键。
     */
    private String dataKey;

    /**
     * 字典数据值。
     */
    private String value;

    /**
     * 排序值。
     */
    private Integer sort;

    /**
     * 状态：1 启用，0 停用。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;
}
