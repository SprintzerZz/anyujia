package com.anyujia.anyujiahouseservice.service;

import com.anyujia.anyujiahouseapi.domain.dto.HouseImageDTO;
import com.anyujia.anyujiahouseapi.domain.vo.HousePublishVO;
import com.anyujia.anyujiahouseapi.domain.vo.HousePublishConsultVO;
import com.anyujia.anyujiahouseservice.domain.HousePublishRow;
import com.anyujia.anyujiahouseservice.mapper.HousePublishMapper;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HousePublishQueryService {
    private final HousePublishMapper housePublishMapper;
    private final ObjectMapper objectMapper;

    public HousePublishQueryService(HousePublishMapper housePublishMapper, ObjectMapper objectMapper) {
        this.housePublishMapper = housePublishMapper;
        this.objectMapper = objectMapper;
    }

    public HousePublishConsultVO getConsultable(Long housePublishId) {
        HousePublishConsultVO result = housePublishMapper.selectConsultable(housePublishId);
        if (result == null) {
            throw new ServiceException("House publication is not consultable", ResultCode.INVALID_PARA.getCode());
        }
        return result;
    }

    public List<HousePublishVO> listPublic(Integer limit) {
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 50));
        return housePublishMapper.selectPublicList(safeLimit).stream().map(this::toVO).toList();
    }

    public HousePublishVO detailPublic(Long publishId) {
        HousePublishRow row = housePublishMapper.selectPublicDetail(publishId);
        if (row == null) {
            throw new ServiceException("House publication is not public", ResultCode.INVALID_PARA.getCode());
        }
        return toVO(row);
    }

    private HousePublishVO toVO(HousePublishRow row) {
        HousePublishVO value = new HousePublishVO();
        value.setPublishId(row.getPublishId());
        value.setHouseId(row.getHouseId());
        value.setHouseCode(row.getHouseCode());
        value.setTitle(row.getTitle());
        value.setCity(row.getCity());
        value.setDistrict(row.getDistrict());
        value.setAddress(row.getAddress());
        value.setMonthlyRent(row.getMonthlyRent());
        value.setAreaSqm(row.getAreaSqm());
        value.setRentType(row.getRentType());
        value.setBedroomCount(row.getBedroomCount());
        value.setLivingRoomCount(row.getLivingRoomCount());
        value.setBathroomCount(row.getBathroomCount());
        value.setDepositMonths(row.getDepositMonths());
        value.setPaymentCycleMonths(row.getPaymentCycleMonths());
        value.setPropertyFee(row.getPropertyFee());
        value.setAvailableFrom(row.getAvailableFrom());
        value.setDescription(row.getDescription());
        value.setRentalRequirements(row.getRentalRequirements());
        value.setFacilities(readList(row.getFacilitiesJson(), new TypeReference<List<String>>() { }));
        value.setCoverImageUrl(row.getCoverImageUrl());
        value.setImages(readList(row.getImageAssetsJson(), new TypeReference<List<HouseImageDTO>>() { }));
        value.setStatus(row.getStatus());
        value.setAuditStatus(row.getAuditStatus());
        value.setAuditRemark(row.getAuditRemark());
        value.setAuditedBy(row.getAuditedBy());
        value.setAuditedAt(row.getAuditedAt());
        value.setSubmittedAt(row.getSubmittedAt());
        value.setCreatedAt(row.getCreatedAt());
        return value;
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }
}
