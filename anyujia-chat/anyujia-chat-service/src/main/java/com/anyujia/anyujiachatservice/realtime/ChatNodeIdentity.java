package com.anyujia.anyujiachatservice.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ChatNodeIdentity {
    private final String nodeId;

    public ChatNodeIdentity(@Value("${chat.node-id:${HOSTNAME:local}}") String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }
}
