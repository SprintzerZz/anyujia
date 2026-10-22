CREATE TABLE IF NOT EXISTS legal_agent_task_outbox (
    id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    payload VARCHAR(2000) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    claim_token VARCHAR(64) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_legal_agent_outbox_event (task_id, event_type),
    KEY idx_legal_agent_outbox_pending (status, next_retry_at, id),
    KEY idx_legal_agent_outbox_claim (status, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Reliable Legal Agent task delivery outbox';
