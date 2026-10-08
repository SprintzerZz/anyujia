package com.anyujia.anyujiahouseapi.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class HousePublishCreateReqDTO {
    @NotBlank(message = "房源标题不能为空")
    @Size(max = 128, message = "房源标题不能超过128个字符")
    private String title;

    @NotBlank(message = "城市不能为空")
    @Size(max = 64, message = "城市不能超过64个字符")
    private String city;

    @NotBlank(message = "区域不能为空")
    @Size(max = 64, message = "区域不能超过64个字符")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255, message = "详细地址不能超过255个字符")
    private String address;

    @NotNull(message = "月租不能为空")
    @DecimalMin(value = "0.01", message = "月租必须大于0")
    private BigDecimal monthlyRent;

    @NotNull(message = "面积不能为空")
    @DecimalMin(value = "0.01", message = "面积必须大于0")
    private BigDecimal areaSqm;

    @NotBlank(message = "出租方式不能为空")
    @Pattern(regexp = "whole|shared", message = "出租方式只能是整租或合租")
    private String rentType;

    @Min(value = 0, message = "卧室数量不能小于0")
    @Max(value = 20, message = "卧室数量不能超过20")
    private Integer bedroomCount = 1;

    @Min(value = 0, message = "客厅数量不能小于0")
    @Max(value = 20, message = "客厅数量不能超过20")
    private Integer livingRoomCount = 1;

    @Min(value = 0, message = "卫生间数量不能小于0")
    @Max(value = 20, message = "卫生间数量不能超过20")
    private Integer bathroomCount = 1;

    @Min(value = 0, message = "押金月数不能小于0")
    @Max(value = 12, message = "押金月数不能超过12")
    private Integer depositMonths = 1;

    @Min(value = 1, message = "付款周期至少为1个月")
    @Max(value = 12, message = "付款周期不能超过12个月")
    private Integer paymentCycleMonths = 1;

    @DecimalMin(value = "0.00", message = "物业费不能小于0")
    private BigDecimal propertyFee = BigDecimal.ZERO;

    private LocalDate availableFrom;

    @Size(max = 2000, message = "房源描述不能超过2000个字符")
    private String description;

    @Size(max = 1000, message = "租住要求不能超过1000个字符")
    private String rentalRequirements;

    @Size(max = 30, message = "房源设施不能超过30项")
    private List<@Size(max = 32, message = "设施名称不能超过32个字符") String> facilities = new ArrayList<>();

    @Size(max = 1024, message = "封面地址不能超过1024个字符")
    private String coverImageUrl;

    @Size(max = 12, message = "房源图片不能超过12张")
    private List<HouseImageDTO> images = new ArrayList<>();

    @NotNull(message = "发布方式不能为空")
    private Boolean publishNow = false;
}
