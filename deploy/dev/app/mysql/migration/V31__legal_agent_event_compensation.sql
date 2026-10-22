ALTER TABLE legal_agent_task_event
    ADD COLUMN redis_published TINYINT NOT NULL DEFAULT 0 COMMENT '是否已发布到Redis Stream';

CREATE INDEX idx_legal_agent_event_publish
    ON legal_agent_task_event (redis_published, id);
