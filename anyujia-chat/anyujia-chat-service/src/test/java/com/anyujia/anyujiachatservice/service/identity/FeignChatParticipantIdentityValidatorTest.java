package com.anyujia.anyujiachatservice.service.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anyujia.anyujiaadminapi.app.user.domain.vo.ChatParticipantIdentityVO;
import com.anyujia.anyujiaadminapi.app.user.feign.AppUserIdentityFeignClient;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import org.junit.jupiter.api.Test;

class FeignChatParticipantIdentityValidatorTest {
    @Test
    void rejectsUnverifiedLandlord() {
        AppUserIdentityFeignClient client = mock(AppUserIdentityFeignClient.class);
        ChatParticipantIdentityVO identity = new ChatParticipantIdentityVO();
        identity.setTenantActive(true);
        identity.setLandlordActive(true);
        identity.setLandlordVerified(false);
        when(client.checkChatParticipants(any())).thenReturn(ApiResult.success(identity));

        FeignChatParticipantIdentityValidator validator = new FeignChatParticipantIdentityValidator(client);

        assertThatThrownBy(() -> validator.validate(10L, 20L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Landlord identity is not verified");
    }
}
