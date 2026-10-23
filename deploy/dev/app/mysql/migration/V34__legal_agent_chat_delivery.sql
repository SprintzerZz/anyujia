ALTER TABLE legal_agent_task
    ADD COLUMN chat_delivery_state TINYINT NOT NULL DEFAULT 0
        COMMENT '0 pending, 1 claimed, 2 delivered' AFTER context_conflict,
    ADD COLUMN chat_delivery_claimed_at DATETIME(3) DEFAULT NULL
        AFTER chat_delivery_state,
    ADD KEY idx_legal_agent_chat_delivery (status, chat_delivery_state, id);
