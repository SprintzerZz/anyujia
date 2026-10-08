package com.anyujia.anyujiaadminapi.config.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 系统参数编辑请求 DTO。
 */
@Data
public class ArgumentEditReqDTO {

    /**
     * 参数键名，用于定位需要编辑的参数。
     */
    @NotBlank(message = "参数键名不能为空")
    private String configKey;

    /**
     * 参数名称。
     */
    @NotBlank(message = "参数名称不能为空")
    private String name;

    /**
     * 参数键值。
     */
    @NotBlank(message = "参数键值不能为空")
    private String value;

    /**
     * 状态：1 启用，0 停用；不传则保留原状态。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;
}
