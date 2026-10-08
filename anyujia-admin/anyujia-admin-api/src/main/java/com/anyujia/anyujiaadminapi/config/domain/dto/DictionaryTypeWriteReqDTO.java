package com.anyujia.anyujiaadminapi.config.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 字典类型写操作请求 DTO。
 *
 * <p>用于新增或修改字典类型时接收前端提交的数据。</p>
 */
@Data
public class DictionaryTypeWriteReqDTO {

    /**
     * 字典类型值，例如房源状态。
     */
    @NotNull(message = "字典类型值不能为空")
    private String value;

    /**
     * 字典类型键，例如 house_status。
     */
    @NotNull(message = "字典类型键不能为空")
    private String typeKey;

    /**
     * 备注。
     */
    private String remark;
}
