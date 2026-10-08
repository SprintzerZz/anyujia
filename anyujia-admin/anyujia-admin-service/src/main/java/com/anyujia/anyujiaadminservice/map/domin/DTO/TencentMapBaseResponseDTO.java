package com.anyujia.anyujiaadminservice.map.domin.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 腾讯位置服务响应基类。
 */
@Data
public class TencentMapBaseResponseDTO {

    /**
     * 响应码，0 表示成功。
     */
    private Integer status;

    /**
     * 响应消息。
     */
    private String message;

    /**
     * 请求 ID，对应腾讯接口返回字段 request_id。
     */
    @JsonProperty("request_id")
    private String requestId;
}
