package com.anyujia.anyujiahouseapi.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class HousePublishConsultVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long housePublishId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long houseId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long landlordAppUserId;

    public Long getHousePublishId() {
        return housePublishId;
    }

    public void setHousePublishId(Long housePublishId) {
        this.housePublishId = housePublishId;
    }

    public Long getHouseId() {
        return houseId;
    }

    public void setHouseId(Long houseId) {
        this.houseId = houseId;
    }

    public Long getLandlordAppUserId() {
        return landlordAppUserId;
    }

    public void setLandlordAppUserId(Long landlordAppUserId) {
        this.landlordAppUserId = landlordAppUserId;
    }
}
