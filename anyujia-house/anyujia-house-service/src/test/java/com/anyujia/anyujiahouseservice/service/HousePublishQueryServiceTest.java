package com.anyujia.anyujiahouseservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.anyujia.anyujiahouseapi.domain.vo.HousePublishConsultVO;
import com.anyujia.anyujiahouseservice.domain.HousePublishRow;
import com.anyujia.anyujiahouseservice.mapper.HousePublishMapper;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HousePublishQueryServiceTest {
    @Mock
    private HousePublishMapper mapper;
    private HousePublishQueryService service;

    @BeforeEach
    void setUp() {
        service = new HousePublishQueryService(mapper, new ObjectMapper());
    }

    @Test
    void publicListMapsApprovedRowsWithImages() {
        HousePublishRow row = publicRow();
        when(mapper.selectPublicList(20)).thenReturn(List.of(row));

        var result = service.listPublic(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPublishId()).isEqualTo(7L);
        assertThat(result.get(0).getImages()).hasSize(1);
        assertThat(result.get(0).getFacilities()).containsExactly("Elevator");
    }

    @Test
    void publicDetailRejectsNonPublicRows() {
        when(mapper.selectPublicDetail(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.detailPublic(7L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void consultableStillUsesExistingInternalContract() {
        HousePublishConsultVO vo = new HousePublishConsultVO();
        vo.setHousePublishId(7L);
        vo.setHouseId(8L);
        vo.setLandlordAppUserId(9L);
        when(mapper.selectConsultable(7L)).thenReturn(vo);

        assertThat(service.getConsultable(7L).getLandlordAppUserId()).isEqualTo(9L);
    }

    private HousePublishRow publicRow() {
        HousePublishRow row = new HousePublishRow();
        row.setPublishId(7L);
        row.setHouseId(8L);
        row.setTitle("Approved house");
        row.setCity("Shenzhen");
        row.setDistrict("Nanshan");
        row.setMonthlyRent(new BigDecimal("5200"));
        row.setFacilitiesJson("[\"Elevator\"]");
        row.setImageAssetsJson("[{\"url\":\"https://cdn.test/room.jpg\",\"key\":\"houses/room.jpg\"}]");
        row.setStatus(1);
        row.setAuditStatus(2);
        return row;
    }
}
