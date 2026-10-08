package com.anyujia.anyujiahouseservice.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class HousePublishRow {
    private Long publishId;
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
    private String facilitiesJson;
    private String coverImageUrl;
    private String imageAssetsJson;
    private Integer status;
    private Integer auditStatus;
    private String auditRemark;
    private Long auditedBy;
    private LocalDateTime auditedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
}
