package com.anyujia.anyujiahouseapi.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.anyujia.anyujiahouseapi.domain.dto.HouseImageDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class HousePublishVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long publishId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long houseId;
    private String houseCode;
    private String title;
    private String city;
    private String district;
    private String address;
    private BigDecimal monthlyRent;
    private BigDecimal areaSqm;
    private String rentType;
    private Integer bedroomCount;
    private Integer livingRoomCount;
    private Integer bathroomCount;
    private Integer depositMonths;
    private Integer paymentCycleMonths;
    private BigDecimal propertyFee;
    private LocalDate availableFrom;
    private String description;
    private String rentalRequirements;
    private List<String> facilities;
    private String coverImageUrl;
    private List<HouseImageDTO> images;
    private Integer status;
    private Integer auditStatus;
    private String auditRemark;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long auditedBy;
    private LocalDateTime auditedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
}
