package com.anyujia.anyujiaadminservice.user.service.impl;

import com.anyujia.anyujiaadminapi.app.user.constants.AppUserIdentityConstants;
import com.anyujia.anyujiaadminapi.app.user.domain.dto.ChatParticipantIdentityReqDTO;
import com.anyujia.anyujiaadminapi.app.user.domain.vo.ChatParticipantIdentityVO;
import com.anyujia.anyujiaadminservice.user.domain.entity.AppUser;
import com.anyujia.anyujiaadminservice.user.domain.entity.LandlordUser;
import com.anyujia.anyujiaadminservice.user.mapper.AppUserMapper;
import com.anyujia.anyujiaadminservice.user.mapper.LandlordUserMapper;
import com.anyujia.anyujiaadminservice.user.mapper.TenantUserMapper;
import com.anyujia.anyujiaadminservice.user.service.IAppUserIdentityService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserIdentityServiceImpl implements IAppUserIdentityService {
    private final AppUserMapper appUserMapper;
    private final TenantUserMapper tenantUserMapper;
    private final LandlordUserMapper landlordUserMapper;

    @Override
    public void ensureTenantIdentity(Long appUserId) {
        if (appUserId != null) {
            tenantUserMapper.insertEnabledIfAbsent(appUserId);
        }
    }

    @Override
    public ChatParticipantIdentityVO checkChatParticipants(ChatParticipantIdentityReqDTO request) {
        Long tenantUserId = request.getTenantUserId();
        Long landlordUserId = request.getLandlordUserId();
        Map<Long, AppUser> users = appUserMapper.selectBatchIds(List.of(tenantUserId, landlordUserId)).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity(), (left, right) -> left));

        AppUser tenantAccount = users.get(tenantUserId);
        boolean tenantAccountActive = isActive(tenantAccount);
        boolean tenantActive = tenantAccountActive
                && tenantUserMapper.countActiveByAppUserId(tenantUserId) > 0;

        AppUser landlordAccount = users.get(landlordUserId);
        LandlordUser landlord = landlordUserMapper.selectByAppUserId(landlordUserId);
        boolean landlordActive = isActive(landlordAccount)
                && landlord != null
                && Integer.valueOf(AppUserIdentityConstants.STATUS_ENABLED).equals(landlord.getStatus());
        boolean landlordVerified = landlordActive
                && Integer.valueOf(AppUserIdentityConstants.LANDLORD_VERIFY_APPROVED)
                        .equals(landlord.getVerifyStatus());

        ChatParticipantIdentityVO result = new ChatParticipantIdentityVO();
        result.setTenantUserId(tenantUserId);
        result.setTenantActive(tenantActive);
        result.setLandlordUserId(landlordUserId);
        result.setLandlordActive(landlordActive);
        result.setLandlordVerified(landlordVerified);
        return result;
    }

    private boolean isActive(AppUser user) {
        return user != null
                && Integer.valueOf(AppUserIdentityConstants.STATUS_ENABLED).equals(user.getStatus());
    }
}
