package com.anyujia.anyujiachatservice.actuator;

import com.anyujia.anyujiachatservice.mq.service.ChatPersistDlqReplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Endpoint(id = "chatPersistDlq")
@RequiredArgsConstructor
public class ChatPersistDlqEndpoint {
    private final ChatPersistDlqReplayService replayService;

    @WriteOperation
    public Map<String, Object> replay() {
        int replayed = replayService.replay(100);
        return Map.of("replayed", replayed, "status", "committed");
    }
}
