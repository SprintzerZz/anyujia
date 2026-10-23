CREATE TABLE IF NOT EXISTS legal_agent_stage_checkpoint (
    id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    stage VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    input_fingerprint VARCHAR(64) DEFAULT NULL,
    output_json JSON DEFAULT NULL,
    error_message VARCHAR(1000) DEFAULT NULL,
    started_at DATETIME(3) DEFAULT NULL,
    completed_at DATETIME(3) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_legal_agent_stage_checkpoint (task_id, stage),
    KEY idx_legal_agent_stage_status (status, updated_at),
    KEY idx_legal_agent_stage_task (task_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Recoverable Legal Agent stage checkpoints';

ALTER TABLE legal_source
    ADD KEY idx_legal_source_version_scope (title, region_code, status, effective_from, effective_to);
