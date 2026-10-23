ALTER TABLE legal_agent_task
    ADD COLUMN replay_from_stage VARCHAR(64) DEFAULT NULL COMMENT 'Checkpoint stage from which this task is replayed' AFTER review_required;

ALTER TABLE legal_agent_review
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER answer_json,
    ADD COLUMN lease_owner VARCHAR(128) DEFAULT NULL AFTER attempt_count,
    ADD COLUMN lease_until DATETIME(3) DEFAULT NULL AFTER lease_owner,
    ADD KEY idx_legal_agent_review_lease (status, lease_until, created_at);
