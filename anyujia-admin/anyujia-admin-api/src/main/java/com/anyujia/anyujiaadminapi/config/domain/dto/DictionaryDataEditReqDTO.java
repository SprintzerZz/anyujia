package com.anyujia.anyujiaadminapi.config.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 字典数据编辑请求 DTO。
 */
@Data
public class DictionaryDataEditReqDTO {

    /**
     * 字典数据主键 ID。
     */
    @NotNull(message = "字典数据ID不能为空")
    private Long id;

    /**
     * 字典类型键。
     */
    @NotNull(message = "字典类型键不能为空")
    private String typeKey;

    /**
     * 字典数据业务键。
     */
    @NotNull(message = "字典数据业务键不能为空")
    private String dataKey;

    /**
     * 字典数据值。
     */
    @NotNull(message = "字典数据值不能为空")
    private String value;

    /**
     * 排序值，数值越小越靠前。
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
