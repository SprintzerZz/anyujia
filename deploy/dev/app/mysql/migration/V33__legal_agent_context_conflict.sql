ALTER TABLE legal_agent_task
    ADD COLUMN context_conflict TINYINT(1) NOT NULL DEFAULT 0
    COMMENT 'Whether grounding changed while the answer was generated' AFTER context_status;
