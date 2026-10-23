ALTER TABLE `chat_conversation`
    ADD COLUMN `conversation_type` int NOT NULL DEFAULT 1 AFTER `landlord_user_id`;

CREATE TABLE IF NOT EXISTS `legal_agent_conversation`
(
    `id`                       bigint      NOT NULL,
    `tenant_id`                bigint      NOT NULL,
    `chat_conversation_id`     bigint      NOT NULL,
    `landlord_conversation_id` bigint      NOT NULL,
    `contract_id`              bigint      NOT NULL,
    `assessment_job_id`        bigint               DEFAULT NULL,
    `status`                   tinyint     NOT NULL DEFAULT 1,
    `created_at`               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`               datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_legal_agent_conversation` (`chat_conversation_id`),
    KEY `idx_legal_agent_tenant` (`tenant_id`, `chat_conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Legal agent conversation grounding bindings';
