package com.anyujia.anyujiachatservice.service.profile;

import com.anyujia.anyujiaadminapi.app.user.domain.vo.AppUserVO;
import com.anyujia.anyujiachatapi.domain.vo.ChatUserVO;
import java.util.Collection;
import java.util.Map;

public interface ChatUserProfileProvider {
    Map<Long, AppUserVO> getRequiredUsers(Collection<Long> userIds);

    Map<Long, AppUserVO> getUsers(Collection<Long> userIds);

    ChatUserVO toChatUser(AppUserVO user, Long userId, Integer userType);
}
