CREATE TABLE IF NOT EXISTS legal_agent_memory_snapshot (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    chat_conversation_id BIGINT NOT NULL,
    snapshot_version BIGINT NOT NULL DEFAULT 1,
    summary TEXT NOT NULL,
    facts_json JSON DEFAULT NULL,
    context_hash CHAR(64) DEFAULT NULL,
    last_message_at DATETIME(3) DEFAULT NULL,
    expires_at DATETIME(3) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_legal_agent_memory_scope (tenant_id, chat_conversation_id),
    KEY idx_legal_agent_memory_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Persistent long-term memory snapshots for Legal Agent';

CREATE TABLE IF NOT EXISTS legal_agent_review (
    id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(512) DEFAULT NULL,
    support_score DECIMAL(8,4) DEFAULT NULL,
    reviewer_id BIGINT DEFAULT NULL,
    reviewer_comment VARCHAR(1000) DEFAULT NULL,
    answer_json JSON DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    reviewed_at DATETIME(3) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_legal_agent_review_task (task_id),
    KEY idx_legal_agent_review_queue (tenant_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Human review gate for low-supported Legal Agent conclusions';

ALTER TABLE legal_agent_task
    ADD COLUMN review_required TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether human review is required' AFTER context_conflict;
