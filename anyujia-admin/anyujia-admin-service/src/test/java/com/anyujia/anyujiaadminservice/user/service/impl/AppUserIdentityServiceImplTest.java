package com.anyujia.anyujiaadminservice.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anyujia.anyujiaadminapi.app.user.constants.AppUserIdentityConstants;
import com.anyujia.anyujiaadminapi.app.user.domain.dto.ChatParticipantIdentityReqDTO;
import com.anyujia.anyujiaadminapi.app.user.domain.vo.ChatParticipantIdentityVO;
import com.anyujia.anyujiaadminservice.user.domain.entity.AppUser;
import com.anyujia.anyujiaadminservice.user.domain.entity.LandlordUser;
import com.anyujia.anyujiaadminservice.user.mapper.AppUserMapper;
import com.anyujia.anyujiaadminservice.user.mapper.LandlordUserMapper;
import com.anyujia.anyujiaadminservice.user.mapper.TenantUserMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppUserIdentityServiceImplTest {
    @Test
    void returnsActiveOnlyWhenAccountsRolesAndLandlordApprovalAreValid() {
        AppUserMapper appUserMapper = mock(AppUserMapper.class);
        TenantUserMapper tenantUserMapper = mock(TenantUserMapper.class);
        LandlordUserMapper landlordUserMapper = mock(LandlordUserMapper.class);
        AppUserIdentityServiceImpl service = new AppUserIdentityServiceImpl(
                appUserMapper, tenantUserMapper, landlordUserMapper);

        when(appUserMapper.selectBatchIds(anyCollection())).thenReturn(List.of(activeUser(10L), activeUser(20L)));
        when(tenantUserMapper.countActiveByAppUserId(10L)).thenReturn(1);
        LandlordUser landlord = new LandlordUser();
        landlord.setAppUserId(20L);
        landlord.setStatus(AppUserIdentityConstants.STATUS_ENABLED);
        landlord.setVerifyStatus(AppUserIdentityConstants.LANDLORD_VERIFY_APPROVED);
        when(landlordUserMapper.selectByAppUserId(20L)).thenReturn(landlord);

        ChatParticipantIdentityReqDTO request = new ChatParticipantIdentityReqDTO();
        request.setTenantUserId(10L);
        request.setLandlordUserId(20L);
        ChatParticipantIdentityVO result = service.checkChatParticipants(request);

        assertThat(result.isTenantActive()).isTrue();
        assertThat(result.isLandlordActive()).isTrue();
        assertThat(result.isLandlordVerified()).isTrue();
    }

    private AppUser activeUser(Long id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setStatus(AppUserIdentityConstants.STATUS_ENABLED);
        return user;
    }
}
