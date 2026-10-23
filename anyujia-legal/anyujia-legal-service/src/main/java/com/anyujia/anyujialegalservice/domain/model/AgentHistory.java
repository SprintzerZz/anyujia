package com.anyujia.anyujialegalservice.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentHistory {
    private Long tenantId;
    private Long chatConversationId;
    private List<Turn> turns = new ArrayList<>();
    private LocalDateTime updatedAt;

    @Data
    public static class Turn {
        private String role;
        private String content;
    }
}
