package com.anyujia.anyujiahouseservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anyujia.anyujiahouseapi.domain.dto.HouseImageDTO;
import com.anyujia.anyujiahouseapi.domain.dto.HousePublishAuditReqDTO;
import com.anyujia.anyujiahouseapi.domain.dto.HousePublishCreateReqDTO;
import com.anyujia.anyujiahouseservice.domain.HousePublishRow;
import com.anyujia.anyujiahouseservice.mapper.LandlordHousePublishMapper;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LandlordHousePublishServiceTest {
    @Mock
    private LandlordHousePublishMapper mapper;
    private LandlordHousePublishService service;

    @BeforeEach
    void setUp() {
        service = new LandlordHousePublishService(mapper, new ObjectMapper());
    }

    @Test
    void rejectsTenantIdentityBeforeWriting() {
        assertThatThrownBy(() -> service.create(10L, 1, "app", request(false)))
                .isInstanceOf(ServiceException.class);
        verify(mapper, never()).insertHouse(any(), anyLong());
    }

    @Test
    void rejectsNonAppCallerBeforeWriting() {
        assertThatThrownBy(() -> service.create(20L, 2, "service", request(false)))
                .isInstanceOf(ServiceException.class);
        verify(mapper, never()).insertHouse(any(), anyLong());
    }

    @Test
    void rejectsLandlordWhoseIdentityIsNotApproved() {
        when(mapper.countApprovedLandlord(20L)).thenReturn(0);
        assertThatThrownBy(() -> service.create(20L, 2, "app", request(false)))
                .isInstanceOf(ServiceException.class);
        verify(mapper, never()).insertHouse(any(), anyLong());
    }

    @Test
    void savesDraftWithStructuredFieldsImagesAndNoSubmissionTime() {
        when(mapper.countApprovedLandlord(20L)).thenReturn(1);
        when(mapper.insertHouse(any(), eq(20L))).thenAnswer(invocation -> {
            invocation.<HousePublishRow>getArgument(0).setHouseId(88L);
            return 1;
        });
        when(mapper.insertPublication(anyLong(), eq(88L), eq(20L), eq(0), eq(null))).thenReturn(1);

        var result = service.create(20L, 2, "app", request(false));

        ArgumentCaptor<HousePublishRow> row = ArgumentCaptor.forClass(HousePublishRow.class);
        verify(mapper).insertHouse(row.capture(), eq(20L));
        assertThat(row.getValue().getTitle()).isEqualTo("Nanshan two-bedroom");
        assertThat(row.getValue().getMonthlyRent()).isEqualByComparingTo("5200");
        assertThat(row.getValue().getFacilitiesJson()).isEqualTo("[\"Air conditioner\",\"Washer\"]");
        assertThat(row.getValue().getImageAssetsJson()).contains("https://cdn.test/room-a.jpg");
        assertThat(result.getStatus()).isZero();
        assertThat(result.getAuditStatus()).isZero();
        assertThat(result.getFacilities()).containsExactly("Air conditioner", "Washer");
        assertThat(result.getImages()).hasSize(1);
    }

    @Test
    void submittedPublicationAlwaysStartsPendingReview() {
        when(mapper.countApprovedLandlord(20L)).thenReturn(1);
        when(mapper.insertHouse(any(), eq(20L))).thenAnswer(invocation -> {
            invocation.<HousePublishRow>getArgument(0).setHouseId(89L);
            return 1;
        });
        when(mapper.insertPublication(anyLong(), eq(89L), eq(20L), eq(1), any(LocalDateTime.class)))
                .thenReturn(1);

        var result = service.create(20L, 2, "app", request(true));

        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getAuditStatus()).isZero();
        assertThat(result.getSubmittedAt()).isNotNull();
    }

    @Test
    void listsOnlyRowsReturnedByOwnerScopedMapper() {
        when(mapper.countApprovedLandlord(20L)).thenReturn(1);
        HousePublishRow row = new HousePublishRow();
        row.setPublishId(99L);
        row.setTitle("My house");
        row.setFacilitiesJson("[\"Elevator\"]");
        row.setImageAssetsJson("[{\"url\":\"https://cdn.test/room.jpg\",\"key\":\"houses/room.jpg\"}]");
        row.setStatus(1);
        row.setAuditStatus(0);
        when(mapper.selectMine(20L)).thenReturn(List.of(row));

        var result = service.listMine(20L, 2, "app");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPublishId()).isEqualTo(99L);
        assertThat(result.get(0).getFacilities()).containsExactly("Elevator");
        assertThat(result.get(0).getImages()).hasSize(1);
        verify(mapper).selectMine(20L);
    }

    @Test
    void adminCanApprovePublication() {
        HousePublishAuditReqDTO audit = new HousePublishAuditReqDTO();
        audit.setAction("approve");
        audit.setRemark("ok");
        HousePublishRow row = new HousePublishRow();
        row.setPublishId(101L);
        row.setStatus(1);
        row.setAuditStatus(2);
        row.setAuditRemark("ok");
        when(mapper.audit(101L, 2, "ok", 900L)).thenReturn(1);
        when(mapper.selectByPublishId(101L)).thenReturn(row);

        var result = service.audit(900L, "admin", 101L, audit);

        assertThat(result.getAuditStatus()).isEqualTo(2);
        assertThat(result.getAuditRemark()).isEqualTo("ok");
    }

    @Test
    void rejectsNonAdminAudit() {
        HousePublishAuditReqDTO audit = new HousePublishAuditReqDTO();
        audit.setAction("reject");

        assertThatThrownBy(() -> service.audit(20L, "app", 101L, audit))
                .isInstanceOf(ServiceException.class);
        verify(mapper, never()).audit(anyLong(), any(), any(), anyLong());
    }

    private HousePublishCreateReqDTO request(boolean publishNow) {
        HousePublishCreateReqDTO request = new HousePublishCreateReqDTO();
        request.setTitle(" Nanshan two-bedroom ");
        request.setCity("Shenzhen");
        request.setDistrict("Nanshan");
        request.setAddress("Tech Park 11");
        request.setMonthlyRent(new BigDecimal("5200"));
        request.setAreaSqm(new BigDecimal("68.5"));
        request.setRentType("whole");
        request.setBedroomCount(2);
        request.setLivingRoomCount(1);
        request.setBathroomCount(1);
        request.setDepositMonths(1);
        request.setPaymentCycleMonths(1);
        request.setPropertyFee(BigDecimal.ZERO);
        request.setFacilities(List.of("Air conditioner", "Washer", "Air conditioner"));
        HouseImageDTO image = new HouseImageDTO();
        image.setUrl("https://cdn.test/room-a.jpg");
        image.setKey("houses/room-a.jpg");
        request.setImages(List.of(image));
        request.setPublishNow(publishNow);
        return request;
    }
}
