package com.anyujia.anyujiachatservice.realtime;

import java.util.Set;

public interface ChatRecipientNodeLocator {
    Set<String> nodes(Long userId);
}
