package com.anyujia.anyujiachatservice.service.profile;

import com.anyujia.anyujiaadminapi.app.user.domain.vo.AppUserVO;
import com.anyujia.anyujiaadminapi.app.user.feign.AppUserFeignClient;
import com.anyujia.anyujiachatapi.constants.ChatConstants;
import com.anyujia.anyujiachatapi.domain.vo.ChatUserVO;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatUserProfileService implements ChatUserProfileProvider {
    private final AppUserFeignClient appUserFeignClient;

    @Override
    public Map<Long, AppUserVO> getRequiredUsers(Collection<Long> userIds) {
        List<Long> realIds = userIds.stream()
                .filter(id -> id != null && id != ChatConstants.AGENT_USER_ID).toList();
        if (realIds.isEmpty()) {
            return Collections.emptyMap();
        }
        ApiResult<List<AppUserVO>> result = appUserFeignClient.listByIds(realIds);
        if (result == null || result.getCode() != ResultCode.SUCCESS.getCode() || result.getData() == null) {
            throw new ServiceException("Failed to load chat users");
        }
        Map<Long, AppUserVO> users = result.getData().stream()
                .collect(Collectors.toMap(AppUserVO::getId, Function.identity(), (left, right) -> left));
        if (!users.keySet().containsAll(realIds)) {
            throw new ServiceException("Chat participant does not exist", ResultCode.INVALID_PARA.getCode());
        }
        return users;
    }

    @Override
    public Map<Long, AppUserVO> getUsers(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return getRequiredUsers(userIds);
        } catch (RuntimeException ex) {
            return Collections.emptyMap();
        }
    }

    @Override
    public ChatUserVO toChatUser(AppUserVO user, Long userId, Integer userType) {
        ChatUserVO result = new ChatUserVO();
        result.setUserId(userId);
        result.setUserType(userType);
        if (userId != null && userId == ChatConstants.AGENT_USER_ID) {
            result.setNickname("法律助手");
            return result;
        }
        if (user != null) {
            result.setNickname(user.getNickname());
            result.setAvatarUrl(user.getAvatarUrl());
        }
        return result;
    }
}
