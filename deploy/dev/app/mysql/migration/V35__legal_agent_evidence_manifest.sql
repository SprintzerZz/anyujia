ALTER TABLE legal_agent_task
    ADD COLUMN evidence_manifest_json JSON DEFAULT NULL
        COMMENT 'Source ids and versions used to generate the answer' AFTER context_conflict;
