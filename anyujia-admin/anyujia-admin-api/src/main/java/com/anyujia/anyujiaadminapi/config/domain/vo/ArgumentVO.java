package com.anyujia.anyujiaadminapi.config.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 系统参数响应 VO。
 */
@Data
public class ArgumentVO {

    /**
     * 参数主键 ID。
     */
    private Long id;

    /**
     * 参数名称。
     */
    private String name;

    /**
     * 参数键名。
     */
    private String configKey;

    /**
     * 参数键值。
     */
    private String value;

    /**
     * 状态：1 启用，0 停用。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}
