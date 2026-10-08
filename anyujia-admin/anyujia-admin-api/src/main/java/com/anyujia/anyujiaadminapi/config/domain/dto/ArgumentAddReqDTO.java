package com.anyujia.anyujiaadminapi.config.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 系统参数新增请求 DTO。
 */
@Data
public class ArgumentAddReqDTO {

    /**
     * 参数名称。
     */
    @NotBlank(message = "参数名称不能为空")
    private String name;

    /**
     * 参数键名。
     */
    @NotBlank(message = "参数键名不能为空")
    private String configKey;

    /**
     * 参数键值。
     */
    @NotBlank(message = "参数键值不能为空")
    private String value;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 状态：1 启用，0 停用；不传默认启用。
     */
    private Integer status;
}
