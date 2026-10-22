CREATE TABLE IF NOT EXISTS legal_agent_task_event (
    id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    payload JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_legal_agent_event_replay (tenant_id, task_id, id),
    KEY idx_legal_agent_event_retention (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Durable Legal Agent task events for SSE replay';
