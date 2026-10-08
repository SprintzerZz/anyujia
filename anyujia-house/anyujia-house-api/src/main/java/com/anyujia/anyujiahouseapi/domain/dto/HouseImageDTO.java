package com.anyujia.anyujiahouseapi.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HouseImageDTO {
    @Size(max = 1024)
    private String url;

    @Size(max = 512)
    private String key;
}
