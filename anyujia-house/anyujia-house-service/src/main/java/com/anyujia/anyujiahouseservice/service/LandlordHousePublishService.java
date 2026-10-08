package com.anyujia.anyujiahouseservice.service;

import com.anyujia.anyujiahouseapi.domain.dto.HouseImageDTO;
import com.anyujia.anyujiahouseapi.domain.dto.HousePublishAuditReqDTO;
import com.anyujia.anyujiahouseapi.domain.dto.HousePublishCreateReqDTO;
import com.anyujia.anyujiahouseapi.domain.vo.HousePublishVO;
import com.anyujia.anyujiahouseservice.domain.HousePublishRow;
import com.anyujia.anyujiahouseservice.mapper.LandlordHousePublishMapper;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LandlordHousePublishService {
    private static final int LANDLORD_USER_TYPE = 2;
    private static final int DRAFT = 0;
    private static final int SUBMITTED = 1;
    private static final int APPROVED = 2;
    private static final int REJECTED = 3;

    private final LandlordHousePublishMapper mapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public HousePublishVO create(Long userId, Integer userType, String userFrom,
                                 HousePublishCreateReqDTO request) {
        requireLandlord(userId, userType, userFrom);
        long publishId = IdWorker.getId();
        HousePublishRow row = toRow(request, publishId);
        if (mapper.insertHouse(row, userId) != 1 || row.getHouseId() == null) {
            throw new ServiceException("保存房源失败", ResultCode.ERROR.getCode());
        }
        int status = Boolean.TRUE.equals(request.getPublishNow()) ? SUBMITTED : DRAFT;
        LocalDateTime submittedAt = status == SUBMITTED ? LocalDateTime.now() : null;
        if (mapper.insertPublication(publishId, row.getHouseId(), userId, status, submittedAt) != 1) {
            throw new ServiceException("保存发布记录失败", ResultCode.ERROR.getCode());
        }
        HousePublishRow saved = mapper.selectOwned(publishId, userId);
        return saved == null ? toVO(row, status, 0, submittedAt) : toVO(saved);
    }

    public List<HousePublishVO> listMine(Long userId, Integer userType, String userFrom) {
        requireLandlord(userId, userType, userFrom);
        return mapper.selectMine(userId).stream().map(this::toVO).toList();
    }

    @Transactional
    public HousePublishVO audit(Long auditorId, String userFrom, Long publishId, HousePublishAuditReqDTO request) {
        requireAdmin(auditorId, userFrom);
        int auditStatus = "approve".equals(request.getAction()) ? APPROVED : REJECTED;
        if (mapper.audit(publishId, auditStatus, cleanNullable(request.getRemark()), auditorId) != 1) {
            throw new ServiceException("房源发布记录不存在或不可审核", ResultCode.INVALID_PARA.getCode());
        }
        HousePublishRow row = mapper.selectByPublishId(publishId);
        if (row == null) {
            throw new ServiceException("房源发布记录不存在", ResultCode.INVALID_PARA.getCode());
        }
        return toVO(row);
    }

    private void requireLandlord(Long userId, Integer userType, String userFrom) {
        if (userId == null || !"app".equals(userFrom) || !Integer.valueOf(LANDLORD_USER_TYPE).equals(userType)) {
            throw new ServiceException("仅房东账号可以发布房源", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
        if (mapper.countApprovedLandlord(userId) < 1) {
            throw new ServiceException("房东身份尚未认证通过", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
    }

    private void requireAdmin(Long userId, String userFrom) {
        if (userId == null || !"admin".equals(userFrom)) {
            throw new ServiceException("仅后台管理员可以审核房源", ResultCode.TOKEN_CHECK_FAILED.getCode());
        }
    }

    private HousePublishRow toRow(HousePublishCreateReqDTO request, long publishId) {
        HousePublishRow row = new HousePublishRow();
        row.setPublishId(publishId);
        row.setHouseCode("HOUSE-" + publishId);
        row.setTitle(clean(request.getTitle()));
        row.setCity(clean(request.getCity()));
        row.setDistrict(clean(request.getDistrict()));
        row.setAddress(clean(request.getAddress()));
        row.setMonthlyRent(request.getMonthlyRent());
        row.setAreaSqm(request.getAreaSqm());
        row.setRentType(clean(request.getRentType()));
        row.setBedroomCount(request.getBedroomCount());
        row.setLivingRoomCount(request.getLivingRoomCount());
        row.setBathroomCount(request.getBathroomCount());
        row.setDepositMonths(request.getDepositMonths());
        row.setPaymentCycleMonths(request.getPaymentCycleMonths());
        row.setPropertyFee(request.getPropertyFee());
        row.setAvailableFrom(request.getAvailableFrom());
        row.setDescription(cleanNullable(request.getDescription()));
        row.setRentalRequirements(cleanNullable(request.getRentalRequirements()));
        row.setFacilitiesJson(writeFacilities(request.getFacilities()));
        row.setCoverImageUrl(cleanNullable(request.getCoverImageUrl()));
        row.setImageAssetsJson(writeImages(request.getImages()));
        return row;
    }

    private HousePublishVO toVO(HousePublishRow row) {
        return toVO(row, row.getStatus(), row.getAuditStatus(), row.getSubmittedAt());
    }

    private HousePublishVO toVO(HousePublishRow row, Integer status, Integer auditStatus,
                                LocalDateTime submittedAt) {
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
        value.setFacilities(readFacilities(row.getFacilitiesJson()));
        value.setCoverImageUrl(row.getCoverImageUrl());
        value.setImages(readImages(row.getImageAssetsJson()));
        value.setStatus(status);
        value.setAuditStatus(auditStatus);
        value.setAuditRemark(row.getAuditRemark());
        value.setAuditedBy(row.getAuditedBy());
        value.setAuditedAt(row.getAuditedAt());
        value.setSubmittedAt(submittedAt);
        value.setCreatedAt(row.getCreatedAt());
        return value;
    }

    private String writeFacilities(List<String> facilities) {
        List<String> cleaned = facilities == null ? List.of() : facilities.stream()
                .map(this::clean).filter(value -> !value.isEmpty()).distinct().toList();
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (JsonProcessingException ex) {
            throw new ServiceException("房源设施格式错误", ResultCode.INVALID_PARA.getCode());
        }
    }

    private List<String> readFacilities(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String writeImages(List<HouseImageDTO> images) {
        List<HouseImageDTO> cleaned = images == null ? List.of() : images.stream()
                .map(this::cleanImage)
                .filter(image -> image.getUrl() != null && !image.getUrl().isBlank())
                .limit(12)
                .toList();
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (JsonProcessingException ex) {
            throw new ServiceException("房源图片格式错误", ResultCode.INVALID_PARA.getCode());
        }
    }

    private HouseImageDTO cleanImage(HouseImageDTO source) {
        HouseImageDTO image = new HouseImageDTO();
        if (source == null) {
            return image;
        }
        image.setUrl(cleanNullable(source.getUrl()));
        image.setKey(cleanNullable(source.getKey()));
        return image;
    }

    private List<HouseImageDTO> readImages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<HouseImageDTO>>() { });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String cleanNullable(String value) {
        String cleaned = clean(value);
        return cleaned.isEmpty() ? null : cleaned;
    }
}
