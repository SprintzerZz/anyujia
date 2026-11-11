package com.anyujia.anyujiachatapi.domain.vo;

import lombok.Data;

@Data
public class ChatImageVO {
    private String assetId;
    private String url;
    private String contentType;
    private Long size;
    private Integer width;
    private Integer height;
}
