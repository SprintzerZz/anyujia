CREATE TABLE IF NOT EXISTS legal_agent_task (
    id BIGINT NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    chat_conversation_id BIGINT NOT NULL,
    query_text VARCHAR(4000) NOT NULL,
    region_code VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
    as_of_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(64) DEFAULT NULL,
    lease_until DATETIME(3) DEFAULT NULL,
    answer_json JSON DEFAULT NULL,
    error_message VARCHAR(1000) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_legal_agent_task_request (tenant_id, request_id),
    KEY idx_legal_agent_task_dispatch (status, lease_until, id),
    KEY idx_legal_agent_task_conversation (tenant_id, chat_conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Persistent asynchronous Legal Agent question tasks';
