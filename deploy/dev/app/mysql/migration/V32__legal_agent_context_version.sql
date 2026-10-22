ALTER TABLE legal_agent_task
    ADD COLUMN context_hash CHAR(64) DEFAULT NULL COMMENT 'Grounding context SHA-256 fingerprint' AFTER answer_json,
    ADD COLUMN context_status VARCHAR(16) DEFAULT NULL COMMENT 'FIRST, UNCHANGED, CHANGED, UNKNOWN' AFTER context_hash;

ALTER TABLE legal_agent_task
    ADD KEY idx_legal_agent_task_context (tenant_id, chat_conversation_id, context_hash);
